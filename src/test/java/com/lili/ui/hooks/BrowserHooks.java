package com.lili.ui.hooks;

import com.lili.ui.config.TestConfig;
import com.lili.ui.locators.HealRecorder;
import com.lili.ui.locators.LocatorRegistry;
import com.lili.ui.locators.RouteRegistry;
import com.lili.ui.support.ScenarioContext;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.AfterStep;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.Scenario;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 浏览器生命周期、失败现场取证与自愈上报。
 *
 * <p>线程模型：Playwright 对象非线程安全，用 ThreadLocal 按线程持有，
 * 因此启用 {@code cucumber.execution.parallel.enabled=true} 时每个线程各自
 * 持有一份 Playwright + Browser，不会互相踩踏。
 *
 * <p>取证原则：失败时把「人眼判断所需的最小证据集」直接放进 Cucumber 自带 HTML 报告 —— 截图、
 * DOM、浏览器控制台、失败请求。trace 虽全，但要下载后回放，看报告的人不会那么做。
 */
public class BrowserHooks {

    private static final ThreadLocal<Playwright> PLAYWRIGHT = new ThreadLocal<>();
    private static final ThreadLocal<Browser> BROWSER = new ThreadLocal<>();
    private static final Path ARTIFACT_DIR = Paths.get("ui_test_dwp_temp", "artifacts");

    /** 单个场景每类日志最多保留条数，防止长场景把内存和报告体积撑爆。 */
    private static final int MAX_LOG_LINES = 200;

    private final ScenarioContext ctx;

    /**
     * 场景级诊断缓冲。
     *
     * <p>必须是实例字段而不是静态字段：Playwright 的事件回调在它自己的分发线程上触发，
     * 静态字段在并行执行时会把两个场景的日志串在一起。
     */
    private final List<String> consoleLogs = Collections.synchronizedList(new ArrayList<>());
    private final List<String> pageErrors = Collections.synchronizedList(new ArrayList<>());
    private final List<String> netErrors = Collections.synchronizedList(new ArrayList<>());

    /**
     * 同一场景只挂一次步骤级现场。
     *
     * <p>失败步骤之后若还有步骤（例如 @After 里的清理动作由别的 hook 执行），
     * 每次 @AfterStep 都会因 {@code scenario.isFailed()} 为真而重复取证，
     * 报告里会出现多份雷同的截图，反而看不出第一现场。
     */
    private boolean stepEvidenceAttached;

    public BrowserHooks(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    /**
     * 全局初始化：定位器表与路由表在启动期一次性装载。
     *
     * <p>fail-fast 的原因：定位器 id 重复 / 文件缺失属于契约错误，
     * 必须在第一秒炸掉，而不是等到某条用例超时才发现。
     */
    @BeforeAll
    public static void globalSetup() {
        PlaywrightAssertions.setDefaultAssertionTimeout(TestConfig.get().timeoutMs());
        LocatorRegistry.load();
        RouteRegistry.load();
    }

    @Before(order = 0)
    public void openBrowser() {
        Playwright playwright = PLAYWRIGHT.get();
        if (playwright == null) {
            playwright = Playwright.create();
            PLAYWRIGHT.set(playwright);
        }

        Browser browser = BROWSER.get();
        if (browser == null) {
            BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                    .setHeadless(TestConfig.get().headless())
                    .setSlowMo(TestConfig.get().slowMo());
            String channel = TestConfig.get().channel();
            if (channel != null) {
                options.setChannel(channel);
            }
            browser = playwright.chromium().launch(options);
            BROWSER.set(browser);
        }

        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setBaseURL(TestConfig.get().baseUrl())
                .setViewportSize(TestConfig.get().viewportWidth(), TestConfig.get().viewportHeight()));
        context.setDefaultTimeout(TestConfig.get().timeoutMs());
        context.tracing().start(new Tracing.StartOptions()
                .setScreenshots(true)
                .setSnapshots(true)
                .setSources(true));

        Page page = context.newPage();
        collectDiagnostics(page);

        ctx.setContext(context);
        ctx.setPage(page);
    }

    /**
     * 失败现场的采集时机：**失败发生的那一步之后**，而不是等场景跑完。
     *
     * <p>为什么要绑在 {@code @AfterStep}：等到 {@code @After} 再取，页面可能已经跳走或
     * 被清理，截到的往往不是失败现场。绑在步骤后，拿到的是最接近失败时刻的页面状态。
     *
     * <p>附件落到哪：Cucumber 会把 {@code @AfterStep} 里 attach 的内容记在该 hook 行下，
     * 所以报告里附件显示在场景页的 Hook 行，而不是那条红色 Gherkin 步骤下面。
     * 要精确挂到步骤本身，只能在每个 step 定义内部 attach，代价是每个 step 包一层 try/catch——
     * 收益（缩进一级）不值这个成本，所以这里接受 Hook 行的落点。
     */
    @AfterStep(order = 10)
    public void captureFailedStepEvidence(Scenario scenario) {
        if (scenario.isFailed() && !stepEvidenceAttached) {
            stepEvidenceAttached = true;
            try {
                attachToCucumberReport(scenario, collectEvidence(scenario));
            } catch (Exception e) {
                // 取证永远不能把用例结论从 failed 改写成 error
                System.err.println("[evidence] 步骤级取证失败（不影响用例结论）: " + e);
            }
        }
    }

    @After(order = 100)
    public void closeBrowser(Scenario scenario) {
        List<String> heals = HealRecorder.consume();
        BrowserContext context = ctx.context();
        try {
            // 步骤级取证已经挂过的场景不再重复采集，避免报告里两份雷同现场；
            // 只有失败发生在 @Before / Background 之前（没有对应步骤）时才走这里的兜底。
            if (scenario.isFailed() && !stepEvidenceAttached) {
                captureFailureEvidence(scenario);
            }
            stopTrace(context, scenario, scenario.isFailed());
        } catch (Exception ignored) {
            // 取证失败不得影响用例结论
        } finally {
            if (!heals.isEmpty()) {
                try {
                    scenario.attach(String.join("\n", heals).getBytes(StandardCharsets.UTF_8),
                            "text/plain", "定位器自愈记录（需人工复核）");
                } catch (Exception ignored) {
                    // 场景已关闭时挂不上，日志里仍有记录
                }
                appendHealLog(scenario.getName(), heals);
            }
            if (context != null) {
                context.close();
            }
            ctx.setContext(null);
            ctx.setPage(null);
        }
    }

    @AfterAll
    public static void shutdown() {
        Browser browser = BROWSER.get();
        if (browser != null) {
            browser.close();
            BROWSER.remove();
        }
        Playwright playwright = PLAYWRIGHT.get();
        if (playwright != null) {
            playwright.close();
            PLAYWRIGHT.remove();
        }
    }

    /**
     * 注册诊断监听。
     *
     * <p>只在内存里留摘要，不写文件：把文件留给 trace，
     * 摘要的价值是「打开报告立刻看到红色 500 或 JS 异常」，不需要保真回放。
     */
    private void collectDiagnostics(Page page) {
        page.onConsoleMessage(msg -> addCapped(consoleLogs, "[" + msg.type() + "] " + msg.text()));
        page.onPageError(error -> addCapped(pageErrors, error));
        page.onRequestFailed(request -> {
            if (isDiagnosticRequest(request.resourceType())) {
                addCapped(netErrors, "REQUEST FAILED " + request.method() + " " + request.url());
            }
        });
        page.onResponse(response -> {
            int status = response.status();
            if (status < 400 || !isDiagnosticRequest(response.request().resourceType())) {
                return;
            }
            addCapped(netErrors, status + " " + response.request().method() + " " + response.url());
        });
    }

    /**
     * 只保留接口与文档请求。
     *
     * <p>实测首页会挂掉一批 OSS 商品图，若不过滤，真正的 500 会被图片错误淹掉，
     * 归因时看到的第一行就失真了。静态资源失败在 trace 里仍然可查。
     */
    private static boolean isDiagnosticRequest(String resourceType) {
        return resourceType == null
                || "xhr".equals(resourceType)
                || "fetch".equals(resourceType)
                || "document".equals(resourceType);
    }

    /**
     * 失败现场：摘要 + 截图 + DOM + 控制台 + 网络错误。
     *
     * <p>每一项单独 try：页面已经崩溃时 DOM 抓取会抛异常，
     * 不能因为它失败就丢掉已经拿到的截图。
     */
    private void captureFailureEvidence(Scenario scenario) {
        for (Evidence item : collectEvidence(scenario)) {
            attachToScenario(scenario, item);
        }
    }

    /** 一次取证产出的单个附件；@After 与 @AfterStep 共用同一份采集逻辑，避免两套实现漂移。 */
    private record Evidence(String name, String mime, String suffix, byte[] body) {
    }

    /**
     * 采集失败现场：摘要 + 当前 URL + 截图 + DOM + 控制台 + 网络错误。
     *
     * <p>每一项单独 try：页面已经崩溃时截图会抛异常，
     * 不能因为它失败就丢掉已经拿到的 DOM。只返回采集成功的项，
     * 由调用方决定挂到场景（@After）还是挂到失败步骤（@AfterStep）。
     */
    private List<Evidence> collectEvidence(Scenario scenario) {
        List<Evidence> items = new ArrayList<>();
        Page page = ctx.page();
        String url = "<no page>";
        String title = "<no page>";
        try {
            if (page != null) {
                url = page.url();
                title = page.isClosed() ? "<page closed>" : page.title();
            }
        } catch (Exception ignored) {
            url = "<unavailable>";
            title = "<unavailable>";
        }

        items.add(text("失败现场", "场景: " + scenario.getName() + "\n"
                + "状态: " + scenario.getStatus() + "\n"
                + "URL : " + url + "\n"
                + "标题: " + title));
        items.add(text("当前 URL", url));

        if (page == null || page.isClosed()) {
            return items;
        }

        try {
            byte[] png = page.screenshot(new Page.ScreenshotOptions().setFullPage(false));
            items.add(new Evidence("失败截图", "image/png", ".png", png));
        } catch (Exception ignored) {
            // 截图失败（页面崩溃/被关闭）不阻断其余取证
        }

        try {
            items.add(new Evidence("失败时 DOM", "text/html", ".html",
                    page.content().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ignored) {
            // 同上
        }

        addLines(items, "浏览器控制台", consoleLogs);
        addLines(items, "页面 JS 异常", pageErrors);
        addLines(items, "失败的网络请求", netErrors);
        return items;
    }

    /** 进 Cucumber 自带 HTML 报告的附件白名单。 */
    private static final Set<String> CUCUMBER_ATTACHMENTS =
            Set.of("失败现场", "失败截图", "失败时 DOM");

    /**
     * 同一份现场同时写进 Cucumber 自带的 HTML 报告（{@code scenario.attach}）。
     *
     * <p>只挑截图 / 摘要 / DOM 三项。控制台与网络错误不进来：它们是纯文本、
     * 每条失败动辄上百行，全量回归失败一多，单文件 HTML 会被撑到几十兆，
     * 打开就卡；需要时去 {@code ui_test_dwp_temp/artifacts} 看 trace 回放。
     *
     * <p>注意：在 {@code @AfterStep} 里 attach，Cucumber 会把它记在 hook 步骤下，
     * 所以附件显示在场景页的 Hook 行、而不是那条红色 Gherkin 步骤上。
     * 要精确挂到步骤上，得在 step 定义内部 attach，代价是每个 step 都要包一层。
     */
    private static void attachToCucumberReport(Scenario scenario, List<Evidence> items) {
        for (Evidence item : items) {
            if (CUCUMBER_ATTACHMENTS.contains(item.name())) {
                attachToScenario(scenario, item);
            }
        }
    }

    /** 单项附件写进 Cucumber 自带报告；失败不影响用例结论。 */
    private static void attachToScenario(Scenario scenario, Evidence item) {
        try {
            scenario.attach(item.body(), item.mime(), item.name());
        } catch (Exception ignored) {
            // 报告写失败不影响用例结论
        }
    }

    private static Evidence text(String name, String content) {
        return new Evidence(name, "text/plain", ".txt", content.getBytes(StandardCharsets.UTF_8));
    }

    private static void addLines(List<Evidence> sink, String name, List<String> lines) {
        List<String> snapshot;
        synchronized (lines) {
            if (lines.isEmpty()) {
                return;
            }
            snapshot = new ArrayList<>(lines);
        }
        sink.add(text(name + "（" + snapshot.size() + " 条）", String.join("\n", snapshot)));
    }

    /**
     * trace 落盘；失败时再作为可下载附件挂进报告。
     *
     * <p>必须传流而不是 {@code path.toString()}：后者只是把路径字符串写进报告，
     * 报告里点开是一行文本，拿不到回放文件。
     *
     * <p>通过的场景只落盘不挂附件：trace 单个就有几 MB，全量回归时会让报告体积翻倍，
     * 而通过用例不需要回放——真要看时按场景名去 {@code ui_test_dwp_temp/artifacts} 取。
     */
    private static void stopTrace(BrowserContext context, Scenario scenario, boolean attachToReport) {
        if (context == null) {
            return;
        }
        try {
            Files.createDirectories(ARTIFACT_DIR);
            Path tracePath = ARTIFACT_DIR.resolve(safeName(scenario.getName()) + ".zip");
            context.tracing().stop(new Tracing.StopOptions().setPath(tracePath));
            if (attachToReport) {
                // trace 单个 1~3 MB，内嵌进单文件 HTML 会把报告撑到几十兆；只记回放路径。
                scenario.attach(("回放：npx playwright show-trace " + tracePath).getBytes(StandardCharsets.UTF_8),
                        "text/plain", "playwright trace 路径");
            }
        } catch (Exception ignored) {
            // trace 落盘失败不得影响用例结论
        }
    }

    /** 有上限的追加：超限后直接丢弃，避免异常页面刷日志刷爆内存。 */
    private static void addCapped(List<String> sink, String line) {
        if (line == null) {
            return;
        }
        synchronized (sink) {
            if (sink.size() >= MAX_LOG_LINES) {
                return;
            }
            sink.add(line);
        }
    }

    /**
     * 把自愈记录追加到 JSONL（路径由 {@code UI_HEAL_LOG} 指定）。
     *
     * <p>为什么必须落盘：验证者与 {@code run_stability.py} 靠这个文件识别
     * 「定位器已降级」——报告里的附件是给人看的，脚本读不到，
     * 不落盘的话自愈会被当成 PASS。
     */
    private static void appendHealLog(String scenarioName, List<String> heals) {
        String path = System.getenv("UI_HEAL_LOG");
        if (path == null || path.isBlank()) {
            return;
        }
        Path target = Paths.get(path);
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            StringBuilder builder = new StringBuilder();
            for (String heal : heals) {
                builder.append("{\"test\": \"").append(escape(scenarioName))
                        .append("\", \"detail\": \"").append(escape(heal)).append("\"}\n");
            }
            Files.writeString(target, builder.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // 自愈日志落盘失败不得影响用例结论
        }
    }

    private static String escape(String value) {
        return (value == null ? "" : value).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String safeName(String name) {
        return (name == null ? "scenario" : name).replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }
}
