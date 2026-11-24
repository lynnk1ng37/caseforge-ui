package com.lili.ui.pages;

import com.lili.ui.locators.LocatorRegistry;
import com.lili.ui.locators.RouteRegistry;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * 领券中心页面对象（/coupon）。
 *
 * <p>职责：打开领券中心、点击领取、读取券条目数；不做业务断言。
 * 定位器来源：classpath:/locators/coupon.yaml
 */
public class CouponCenterPage {

    private final Page page;
    private final Locator pageFlag;
    private final Locator items;
    private final Locator receiveButton;

    public CouponCenterPage(Page page) {
        this.page = page;
        this.pageFlag = LocatorRegistry.resolve(page, "coupon.center_flag");
        this.items = LocatorRegistry.resolve(page, "coupon.center_item");
        this.receiveButton = LocatorRegistry.resolve(page, "coupon.receive_btn");
    }

    public CouponCenterPage open() {
        RouteRegistry.open(page, "coupon_center");
        assertThat(pageFlag.first()).isVisible();
        return this;
    }

    public int itemCount() {
        return items.count();
    }

    public boolean pageFlagVisible() {
        return pageFlag.count() > 0 && pageFlag.first().isVisible();
    }

    public int receiveButtonCount() {
        return receiveButton.count();
    }

    /** 点击第一张券的「立即领取」，不判断结果（结果由用例层回查券数量）。 */
    public CouponCenterPage receiveFirst() {
        receiveButton.first().click();
        return this;
    }
}
