package com.lili.ui.config;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 运行期配置（classpath:/config.yaml）。
 *
 * <p>优先级：系统属性（{@code -Dui.headless=false}）> 环境变量 > config.yaml。
 *
 * <p>为什么把优先级放在这里而不是各 page：CI 与本地只差参数，不改代码；
 * 所有层只通过本类取地址与开关，避免出现硬编码 URL。
 */
public final class TestConfig {

    private static final TestConfig INSTANCE = new TestConfig();

    private final Map<String, Object> raw;

    private TestConfig() {
        Map<String, Object> loaded;
        try (InputStream in = TestConfig.class.getResourceAsStream("/config.yaml")) {
            if (in == null) {
                throw new IllegalStateException("classpath 缺少 config.yaml");
            }
            loaded = new Yaml().load(in);
        } catch (Exception e) {
            throw new IllegalStateException("config.yaml 解析失败: " + e.getMessage(), e);
        }
        this.raw = loaded == null ? Map.of() : loaded;
    }

    public static TestConfig get() {
        return INSTANCE;
    }

    public String baseUrl() {
        return env("LILISHOP_UI_BASE_URL", env("LILISHOP_BASE_URL", str("ui.base_url", "http://127.0.0.1")));
    }

    /**
     * 环境标识（local / uat / sit ...）。
     *
     * <p>只作为运行环境标识输出，不参与任何业务分支；
     * 单独给一个入口是为了避免各层各自读环境变量、口径不一致。
     */
    public String envName() {
        String fromProperty = System.getProperty("ui.env");
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty.trim();
        }
        return env("UI_ENV", str("ui.env", "local"));
    }

    public String apiBaseUrl() {
        return env("LILISHOP_API_BASE_URL", str("api.base_url", baseUrl() + "/api"));
    }

    public boolean headless() {
        String fromProperty = System.getProperty("ui.headless");
        if (fromProperty != null && !fromProperty.isBlank()) {
            return Boolean.parseBoolean(fromProperty.trim());
        }
        return Boolean.parseBoolean(env("UI_HEADLESS", String.valueOf(bool("ui.headless", true))));
    }

    public double slowMo() {
        String fromProperty = System.getProperty("ui.slowmo");
        if (fromProperty != null && !fromProperty.isBlank()) {
            return Double.parseDouble(fromProperty.trim());
        }
        return doubleOf("ui.slow_mo", 0d);
    }

    /**
     * 浏览器通道：{@code chrome} 走系统 Chrome，{@code null} 表示用 Playwright 自带浏览器。
     *
     * <p>系统属性优先，是为了支持 {@code -Dui.channel=none}：Linux arm64 上装不了
     * Google Chrome 的官方包，CI 只能用镜像内置的 chromium。这个场景没法用环境变量表达——
     * {@code UI_CHANNEL} 置空会被 {@link #env} 当成未设置而回落到 config.yaml。
     */
    public String channel() {
        String fromProperty = System.getProperty("ui.channel");
        if (fromProperty != null) {
            String trimmed = fromProperty.trim();
            return (trimmed.isEmpty() || "none".equalsIgnoreCase(trimmed)) ? null : trimmed;
        }
        String value = env("UI_CHANNEL", str("ui.channel", "chrome"));
        return value.isBlank() ? null : value;
    }

    public double timeoutMs() {
        return doubleOf("ui.timeout_ms", envDouble("UI_TIMEOUT_MS", 15000d));
    }

    public int viewportWidth() {
        return (int) doubleOf("ui.viewport.width", 1440d);
    }

    public int viewportHeight() {
        return (int) doubleOf("ui.viewport.height", 900d);
    }

    public String username() {
        return env("LILISHOP_USERNAME", str("api.username", ""));
    }

    public String password() {
        return env("LILISHOP_PASSWORD", str("api.password", ""));
    }

    public String goodsId() {
        return env("UI_TEST_GOODS_ID", str("goods.id", ""));
    }

    public String skuId() {
        return env("UI_TEST_SKU_ID", str("goods.sku_id", ""));
    }

    public String goodsName() {
        return env("UI_TEST_GOODS_NAME", str("goods.name", ""));
    }

    public double goodsPrice() {
        return doubleOf("goods.price", envDouble("UI_TEST_GOODS_PRICE", 0d));
    }

    /** 第二件商品（不同店铺），用于多店铺分组与勾选场景。 */
    public String goods2Id() {
        return str("goods2.id", "");
    }

    public String sku2Id() {
        return str("goods2.sku_id", "");
    }

    public double goods2Price() {
        return doubleOf("goods2.price", 0d);
    }

    /** 第三件商品，用于购物车多行删除场景。 */
    public String goods3Id() {
        return str("goods3.id", "");
    }

    public String sku3Id() {
        return str("goods3.sku_id", "");
    }

    /** 下架场景专用商品 id。 */
    public String offShelfGoodsId() {
        return str("off_shelf_goods.id", "");
    }

    /** 需要装载的定位器文件名，按顺序加载。 */
    @SuppressWarnings("unchecked")
    public List<String> locatorFiles() {
        Object value = raw.get("locators");
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<String> files = new ArrayList<>();
        for (Object item : list) {
            if (item != null && !String.valueOf(item).isBlank()) {
                files.add(String.valueOf(item).trim());
            }
        }
        return files;
    }

    // ---------- 取值工具 ----------

    private String str(String path, String fallback) {
        Object value = dig(path);
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private boolean bool(String path, boolean fallback) {
        Object value = dig(path);
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value).trim());
    }

    private double doubleOf(String path, double fallback) {
        Object value = dig(path);
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @SuppressWarnings("unchecked")
    private Object dig(String path) {
        Object current = raw;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private static double envDouble(String key, double fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
