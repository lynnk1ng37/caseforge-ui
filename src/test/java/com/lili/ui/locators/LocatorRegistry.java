package com.lili.ui.locators;

import com.lili.ui.config.TestConfig;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 定位器登记表加载与解析，唯一来源是 classpath:/locators/*.yaml。
 *
 * <p>三条与 Python 版对齐的规则：
 * <ol>
 *   <li>page 层不得内联选择器，只能 {@code LocatorRegistry.resolve(page, "cart.items")}；</li>
 *   <li>解析顺序 primary → fallbacks，命中即返回，走 fallback 时记入 {@link HealRecorder}；</li>
 *   <li>全部未命中时返回 primary，让断言以超时暴露断链，**不静默返回空 locator**。</li>
 * </ol>
 *
 * <p>装载在首次调用时一次性完成，并对 id 重复、文件缺失、格式错误做 fail-fast：
 * 定位器表是全局契约，脏数据必须在启动期炸掉，而不是在某个用例里随机挂。
 */
public final class LocatorRegistry {

    private static final Map<String, Map<String, Object>> SPECS = new LinkedHashMap<>();
    private static volatile boolean loaded;

    private LocatorRegistry() {
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }
        for (String file : TestConfig.get().locatorFiles()) {
            String path = "/locators/" + file;
            try (InputStream in = LocatorRegistry.class.getResourceAsStream(path)) {
                if (in == null) {
                    throw new IllegalStateException("定位器文件不存在于 classpath: " + path);
                }
                List<Map<String, Object>> items = new Yaml().load(in);
                if (items == null) {
                    continue;
                }
                for (Map<String, Object> item : items) {
                    String id = str(item.get("id"));
                    if (id == null) {
                        throw new IllegalStateException("定位器缺少 id: " + path);
                    }
                    if (SPECS.containsKey(id)) {
                        throw new IllegalStateException("定位器 id 重复: " + id + "（" + file + "）");
                    }
                    item.put("_file", file);
                    SPECS.put(id, item);
                }
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("定位器文件解析失败: " + path, e);
            }
        }
        loaded = true;
    }

    /** 按登记表解析定位器；走 fallback 时记录 healed 日志。 */
    public static Locator resolve(Page page, String key) {
        load();
        Map<String, Object> spec = SPECS.get(key);
        if (spec == null) {
            throw new IllegalArgumentException("定位器未登记: " + key);
        }

        List<Map<String, Object>> candidates = new ArrayList<>();
        candidates.add(cast(spec.get("primary")));
        Object fallbacks = spec.get("fallbacks");
        if (fallbacks instanceof List<?> list) {
            for (Object item : list) {
                candidates.add(cast(item));
            }
        }

        for (int index = 0; index < candidates.size(); index++) {
            try {
                Locator locator = build(page, candidates.get(index));
                if (locator.count() > 0) {
                    if (index > 0) {
                        HealRecorder.record(key + ": primary 未命中，使用 fallback[" + index + "] "
                                + candidates.get(index));
                    }
                    return locator;
                }
            } catch (RuntimeException ignored) {
                // 候选写法不被当前版本支持时继续尝试下一个，最终仍以 primary 暴露断链
            }
        }
        return build(page, candidates.get(0));
    }

    private static Locator build(Page page, Map<String, Object> candidate) {
        String by = str(candidate.get("by"));
        if (by == null) {
            throw new IllegalArgumentException("定位器候选缺少 by 字段: " + candidate);
        }

        Locator locator;
        switch (by) {
            case "role" -> locator = page.getByRole(
                    ariaRole(str(candidate.get("role"))),
                    new Page.GetByRoleOptions().setName(str(candidate.get("name"))));
            case "label" -> locator = page.getByLabel(str(candidate.get("value")));
            case "placeholder" -> locator = page.getByPlaceholder(str(candidate.get("value")));
            case "testid" -> locator = page.getByTestId(str(candidate.get("value")));
            case "text" -> locator = page.getByText(
                    str(candidate.get("value")),
                    new Page.GetByTextOptions().setExact(bool(candidate.get("exact"), true)));
            case "css" -> locator = page.locator(str(candidate.get("value")));
            case "xpath" -> locator = page.locator("xpath=" + str(candidate.get("value")));
            default -> throw new IllegalArgumentException("未知定位方式: " + by);
        }

        String filterText = str(candidate.get("text"));
        if (filterText != null && ("css".equals(by) || "xpath".equals(by))) {
            locator = locator.filter(new Locator.FilterOptions().setHasText(filterText));
        }
        Object nth = candidate.get("nth");
        if (nth != null) {
            locator = locator.nth(Integer.parseInt(String.valueOf(nth)));
        }
        return locator;
    }

    private static AriaRole ariaRole(String name) {
        if (name == null) {
            throw new IllegalArgumentException("role 定位缺少 role 字段");
        }
        return AriaRole.valueOf(name.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("定位器候选格式错误（应为映射）: " + value);
        }
        return (Map<String, Object>) map;
    }

    private static String str(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
