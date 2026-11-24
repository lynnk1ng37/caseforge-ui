package com.lili.ui.locators;

import com.lili.ui.config.TestConfig;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 路由唯一来源：classpath:/routes.yaml。
 *
 * <p>page / flow / steps 层禁止出现 {@code http://} 字面量，必须走本类。
 */
public final class RouteRegistry {

    private static final Map<String, String> ROUTES = new LinkedHashMap<>();
    private static volatile boolean loaded;

    private RouteRegistry() {
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }
        try (InputStream in = RouteRegistry.class.getResourceAsStream("/routes.yaml")) {
            if (in == null) {
                throw new IllegalStateException("classpath 缺少 routes.yaml");
            }
            Map<String, Object> raw = new Yaml().load(in);
            if (raw != null) {
                raw.forEach((key, value) -> ROUTES.put(key, String.valueOf(value)));
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("routes.yaml 解析失败", e);
        }
        loaded = true;
    }

    public static String url(String key) {
        return url(key, Map.of());
    }

    public static String url(String key, Map<String, String> query) {
        load();
        String path = ROUTES.get(key);
        if (path == null) {
            throw new IllegalArgumentException("路由未登记: " + key);
        }
        StringBuilder builder = new StringBuilder(TestConfig.get().baseUrl()).append(path);
        if (query != null && !query.isEmpty()) {
            builder.append(path.contains("?") ? '&' : '?');
            boolean first = true;
            for (Map.Entry<String, String> entry : query.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                if (!first) {
                    builder.append('&');
                }
                builder.append(entry.getKey()).append('=').append(entry.getValue());
                first = false;
            }
        }
        return builder.toString();
    }

    public static void open(Page page, String key) {
        open(page, key, Map.of());
    }

    /**
     * 已在目标路由时不重复导航。
     *
     * <p>为什么需要：同一场景里多次调用 {@code open()} 时，向相同 URL 再导航会残留上一份 DOM
     * （实测首页分类由 34 个变成 68 个且第一份不可见），导致 {@code first()} 命中脏节点。
     */
    public static void openIfNeeded(Page page, String key) {
        String target = url(key);
        if (!samePath(target, page.url())) {
            open(page, key);
        }
    }

    /**
     * 只比较路径，避免根路由 {@code /} 用 startsWith 把 {@code /cart} 也判成「已在首页」。
     */
    private static boolean samePath(String left, String right) {
        String leftPath = pathOf(left);
        String rightPath = pathOf(right);
        return leftPath.equals(rightPath);
    }

    private static String pathOf(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            // about:blank 这类 opaque URI 没有 path，不能当作根路由 "/"，否则会跳过导航
            if (uri.isOpaque()) {
                return url;
            }
            String path = uri.getPath();
            return (path == null || path.isEmpty()) ? "/" : path;
        } catch (IllegalArgumentException e) {
            return url;
        }
    }

    /**
     * 统一导航入口。
     *
     * <p>用 {@code DOMCONTENTLOADED} 而不是 {@code LOAD}：lilishop 前端从外部 CDN
     * 加载 vue / iview，CDN 抖动会让 LOAD 一直等不到，把环境问题误判成页面问题。
     * 页面就绪交给各 page object 的断言等待。
     */
    public static void open(Page page, String key, Map<String, String> query) {
        page.navigate(url(key, query),
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
    }
}
