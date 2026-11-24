package com.lili.ui.hooks;

import com.lili.ui.config.TestConfig;
import com.lili.ui.locators.HealRecorder;
import com.lili.ui.locators.LocatorRegistry;
import com.lili.ui.locators.RouteRegistry;
import com.lili.ui.support.AllureEnvironment;
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
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.Scenario;
import io.qameta.allure.Allure;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 浏览器生命周期、失败现场取证与自愈上报。
 *
 * <p>线程模型：Playwright 对象非线程安全，用 ThreadLocal 按线程持有，
 * 因此启用 {@code cucumber.execution.parallel.enabled=true} 时每个线程各自
 * 持有一份 Playwright + Browser，不会互相踩踏。
 *
 * <p>取证原则：失败时把「人眼判断所需的最小证据集」直接放进 Allure —— 截图、
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

    public BrowserHooks(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    /**
     * 全局初始化：定位器表与路由表在启动期一次性装载。
     *
     * <p>fail-fast 的原因：定位器 id 重复 / 文件缺失属于契约错误，
     * 必须在第一秒炸掉，而不是等到某条用例超时才发现。
     *
     * <p>顺带把构建环境元信息写进 allure-results：报告首页的环境面板与
     * 右上角的构建号都来自这里，合并多 job 报告时靠它区分来源。
     */
    @BeforeAll
    public static void globalSetup() {
        PlaywrightAssertions.setDefaultAssertionTimeout(TestConfig.get().timeoutMs());
        LocatorRegistry.load();
        RouteRegistry.load();
        AllureEnvironment.write();
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

    @After(order = 100)
    public void closeBrowser(Scenario scenario) {
        List<String> heals = HealRecorder.consume();
        BrowserContext context = ctx.context();
        try {
            if (scenario.isFailed()) {
                captureFailureEvidence(scenario);
            }
            stopTrace(context, scenario, scenario.isFailed());
        } catch (Exception ignored) {
            // 取证失败不得影响用例结论
        } finally {
            if (!heals.isEmpty()) {
                Allure.addAttachment("定位器自愈记录（需人工复核）", String.join("\n", heals));
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

        attachText("失败现场", "场景: " + scenario.getName() + "\n"
                + "状态: " + scenario.getStatus() + "\n"
                + "URL : " + url + "\n"
                + "标题: " + title);

        if (page == null || page.isClosed()) {
            return;
        }

        try {
            byte[] png = page.screenshot(new Page.ScreenshotOptions().setFullPage(false));
            attachBytes("失败截图", "image/png", ".png", png);
        } catch (Exception ignored) {
            // 截图失败（页面崩溃/被关闭）不阻断其余取证
        }

        try {
            attachText("失败时 DOM", page.content(), "text/html", ".html");
        } catch (Exception ignored) {
            // 同上
        }

        attachLines("浏览器控制台", consoleLogs);
        attachLines("页面 JS 异常", pageErrors);
        attachLines("失败的网络请求", netErrors);
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
                attachFile("playwright trace", "application/zip", tracePath, ".zip");
            }
        } catch (Exception ignored) {
            // trace 落盘失败不得影响用例结论
        }
    }

    private static void attachFile(String name, String mime, Path path, String suffix) {
        if (path == null || !Files.isRegularFile(path)) {
            return;
        }
        try {
            attachBytes(name, mime, suffix, Files.readAllBytes(path));
        } catch (Exception ignored) {
            // 附件写入失败不影响结论
        }
    }

    private static void attachLines(String name, List<String> lines) {
        List<String> snapshot;
        synchronized (lines) {
            if (lines.isEmpty()) {
                return;
            }
            snapshot = new ArrayList<>(lines);
        }
        attachText(name + "（" + snapshot.size() + " 条）", String.join("\n", snapshot));
    }

    private static void attachText(String name, String content) {
        attachText(name, content, "text/plain", ".txt");
    }

    private static void attachText(String name, String content, String mime, String suffix) {
        attach(name, mime, suffix, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void attachBytes(String name, String mime, String suffix, byte[] body) {
        attach(name, mime, suffix, body);
    }

    /**
     * 附件统一入口。
     *
     * <p>注意：allure-cucumber7 会把 hook 内产生的附件搬到 fixture 步骤上并重新落盘，
     * 旧文件会成为无人引用的孤儿（进不了报告，但会留在 results 目录里）。
     * 实测过，用 prepareAttachment + updateTestCase 自己挂也躲不开这次搬运，
     * 所以这里保持最简写法；归档体积若成为问题，应在 CI 侧处理而不是在这里造轮子。
     */
    private static void attach(String name, String mime, String suffix, byte[] body) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(body)) {
            Allure.addAttachment(name, mime, in, suffix);
        } catch (Exception ignored) {
            // 附件写入失败不影响结论
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
     * 「定位器已降级」，只有 Allure 附件时脚本读不到，自愈会被当成 PASS。
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
