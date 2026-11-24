package com.lili.ui.pages;

import com.lili.ui.locators.LocatorRegistry;
import com.lili.ui.locators.RouteRegistry;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * 我的售后列表页页面对象（/home/AfterSale）。
 */
public class AfterSalePage {

    private final Page page;
    private final Locator cardTitle;
    private final Locator emptyTip;

    public AfterSalePage(Page page) {
        this.page = page;
        this.cardTitle = LocatorRegistry.resolve(page, "after_sale.card_title");
        this.emptyTip = LocatorRegistry.resolve(page, "after_sale.empty_tip");
    }

    public AfterSalePage open() {
        RouteRegistry.open(page, "my_after_sale");
        assertThat(cardTitle.first()).isVisible();
        return this;
    }

    public boolean emptyTipVisible() {
        return emptyTip.count() > 0 && emptyTip.first().isVisible();
    }
}
