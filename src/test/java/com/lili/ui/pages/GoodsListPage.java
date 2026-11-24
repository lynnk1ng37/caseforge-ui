package com.lili.ui.pages;

import com.lili.ui.locators.LocatorRegistry;
import com.lili.ui.locators.RouteRegistry;
import com.lili.ui.support.Tabs;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * 商品列表 / 搜索结果页页面对象。
 *
 * <p>职责：按关键字打开列表、读取结果条数与首条信息、打开首条详情。
 * 不做业务断言（是否命中由 steps 判定）。
 *
 * <p>定位器来源：classpath:/locators/goods_list.yaml
 */
public class GoodsListPage {

    private final Page page;
    private final Locator item;
    private final Locator name;
    private final Locator price;
    private final Locator keywordLabel;
    private final Locator activeSort;
    private final Locator totalLabel;

    public GoodsListPage(Page page) {
        this.page = page;
        this.item = LocatorRegistry.resolve(page, "goods_list.item");
        this.name = LocatorRegistry.resolve(page, "goods_list.name");
        this.price = LocatorRegistry.resolve(page, "goods_list.price");
        this.keywordLabel = LocatorRegistry.resolve(page, "goods_list.keyword_label");
        this.activeSort = LocatorRegistry.resolve(page, "goods_list.active_sort");
        this.totalLabel = LocatorRegistry.resolve(page, "goods_list.empty_tip");
    }

    /** 关键字需要 URL 编码后再拼查询串，避免中文/空格导致路由不可达。 */
    public GoodsListPage openByKeyword(String keyword) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("keyword", URLEncoder.encode(keyword, StandardCharsets.UTF_8));
        RouteRegistry.open(page, "goods_list", query);
        assertThat(keywordLabel.first()).isVisible();
        return this;
    }

    public int itemCount() {
        return item.count();
    }

    /**
     * 等结果区渲染完成后再取条数（列表是 XHR 异步渲染，直接 count() 会读到 0）。
     *
     * @return 商品条数；若超时仍无结果则返回 0（无结果场景）
     */
    public int itemCountAwait() {
        try {
            item.first().waitFor(new Locator.WaitForOptions().setTimeout(8000));
        } catch (com.microsoft.playwright.PlaywrightException ignored) {
            // 无结果时不出现商品条目，属正常分支
        }
        return item.count();
    }

    public String firstName() {
        return itemCount() == 0 ? "" : name.first().innerText().trim();
    }

    public String firstPrice() {
        return itemCount() == 0 ? "" : price.first().innerText().trim();
    }

    public String keywordText() {
        return keywordLabel.count() == 0 ? "" : keywordLabel.first().innerText().trim();
    }

    public String totalText() {
        return totalLabel.count() == 0 ? "" : totalLabel.first().innerText().trim();
    }

    public String activeSortText() {
        return activeSort.count() == 0 ? "" : activeSort.first().innerText().trim();
    }

    /** 结果条目以新标签页打开详情，返回详情页 Page。 */
    public Page openFirstItem() {
        Page target = Tabs.clickOpeningNewTab(page, () -> item.first().click());
        assertThat(target).hasURL(Pattern.compile("/goodsDetail"));
        return target;
    }
}
