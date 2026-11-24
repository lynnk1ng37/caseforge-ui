package com.lili.ui.pages;

import com.lili.ui.locators.LocatorRegistry;
import com.lili.ui.locators.RouteRegistry;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * 我的订单页页面对象。
 *
 * <p>职责：切换标签、读取订单卡片文本；不做业务断言。
 * 定位器来源：classpath:/locators/order_list.yaml
 */
public class OrderListPage {

    private static final Pattern ORDER_SN = Pattern.compile("O\\d{15,}");

    private final Page page;
    private final Locator tabs;
    private final Locator activeTab;
    private final Locator rows;

    public OrderListPage(Page page) {
        this.page = page;
        this.tabs = LocatorRegistry.resolve(page, "order_list.tab");
        this.activeTab = LocatorRegistry.resolve(page, "order_list.active_tab");
        this.rows = LocatorRegistry.resolve(page, "order_list.rows");
    }

    public OrderListPage open() {
        RouteRegistry.open(page, "my_orders");
        assertThat(tabs.first()).isVisible();
        // 订单卡片是 XHR 异步渲染，等首行出现，避免 count() 读到 0
        assertThat(rows).not().hasCount(0);
        return this;
    }

    /** 只等待页面就绪，不导航（供跨页导航后的就绪判断）。 */
    public OrderListPage expectLoaded() {
        assertThat(tabs.first()).isVisible();
        return this;
    }

    public int tabCount() {
        return tabs.count();
    }

    public OrderListPage switchTo(String tabText) {
        Locator tab = tabs.filter(new Locator.FilterOptions().setHasText(tabText)).first();
        tab.click();
        assertThat(tab).hasClass(Pattern.compile("active"));
        return this;
    }

    public String activeTabText() {
        return activeTab.count() == 0 ? "" : activeTab.first().innerText().trim();
    }

    public int orderCount() {
        return rows.count();
    }

    public List<String> orderTexts() {
        return rows.allInnerTexts().stream().map(String::trim).toList();
    }

    public String firstOrderText() {
        return rows.count() == 0 ? "" : rows.first().innerText().trim();
    }

    /** 按订单号搜索（输入后回车触发），等待列表或空态刷新。 */
    public OrderListPage searchBySn(String orderSn) {
        Locator search = LocatorRegistry.resolve(page, "order_list.search_input");
        search.first().fill(orderSn);
        search.first().press("Enter");
        return this;
    }

    /** 等待搜索结果稳定（条数为期望值）。 */
    public OrderListPage awaitOrderCount(int expected) {
        assertThat(rows).hasCount(expected);
        return this;
    }

    /** 打开第一笔订单的详情（同页跳转）。 */
    public OrderListPage openFirstDetail() {
        Locator detail = LocatorRegistry.resolve(page, "order_list.detail_button");
        detail.first().click();
        assertThat(page).hasURL(Pattern.compile("/home/OrderDetail"));
        return this;
    }

    /** 取消第一笔待付款订单（弹窗点主按钮确定）。 */
    public OrderListPage cancelFirstOrder() {
        Locator cancel = LocatorRegistry.resolve(page, "order_list.cancel_button");
        cancel.first().click();
        Locator ok = LocatorRegistry.resolve(page, "order_list.modal_ok");
        ok.first().click(new Locator.ClickOptions().setTimeout(8000));
        assertThat(page).hasURL(Pattern.compile("/home/MyOrder"));
        return this;
    }

    /** 从订单卡片文本里提取 O 开头订单号。 */
    public List<String> orderSns() {
        List<String> sns = new ArrayList<>();
        for (String text : orderTexts()) {
            Matcher matcher = ORDER_SN.matcher(text);
            while (matcher.find()) {
                sns.add(matcher.group());
            }
        }
        return sns;
    }
}
