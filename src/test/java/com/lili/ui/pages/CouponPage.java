package com.lili.ui.pages;

import com.lili.ui.locators.LocatorRegistry;
import com.lili.ui.locators.RouteRegistry;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * 我的优惠券页（/home/Coupons）。
 */
public class CouponPage {

    private final Page page;
    private final Locator tabs;
    private final Locator activeTab;
    private final Locator items;
    private final Locator useButtons;

    public CouponPage(Page page) {
        this.page = page;
        this.tabs = LocatorRegistry.resolve(page, "coupon.my_tab");
        this.activeTab = LocatorRegistry.resolve(page, "coupon.my_tab_active");
        this.items = LocatorRegistry.resolve(page, "coupon.my_item");
        this.useButtons = LocatorRegistry.resolve(page, "coupon.use_btn");
    }

    public CouponPage open() {
        RouteRegistry.open(page, "my_coupons");
        assertThat(tabs.first()).isVisible();
        return this;
    }

    /** 原地刷新（SPA 二次进入同一路由时不重新拉列表，需要硬刷新）。 */
    public CouponPage reload() {
        page.reload(new Page.ReloadOptions()
                .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
        assertThat(tabs.first()).isVisible();
        return this;
    }

    public CouponPage switchTo(String tabText) {
        Locator tab = tabs.filter(new Locator.FilterOptions().setHasText(tabText)).first();
        tab.click();
        assertThat(tab).hasClass(java.util.regex.Pattern.compile("active"));
        return this;
    }

    public String activeTabText() {
        return activeTab.count() == 0 ? "" : activeTab.first().innerText().trim();
    }

    /** 等券列表渲染到指定条数（列表是 XHR 异步渲染）。 */
    public CouponPage awaitItemCount(int expected) {
        assertThat(items).hasCount(expected);
        return this;
    }

    /** 短超时等待条数，用于「失败再重试」的分支。 */
    public boolean tryAwaitItemCount(int expected, double timeoutMs) {
        try {
            items.first().or(items).first();
            assertThat(items).hasCount(expected);
            return true;
        } catch (AssertionError | com.microsoft.playwright.PlaywrightException e) {
            return false;
        }
    }

    public int itemCount() {
        return items.count();
    }

    public int useButtonCount() {
        return useButtons.count();
    }

    public String firstItemText() {
        return items.count() == 0 ? "" : items.first().innerText().trim();
    }
}
