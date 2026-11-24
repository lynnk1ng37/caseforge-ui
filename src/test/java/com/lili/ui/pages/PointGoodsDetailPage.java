package com.lili.ui.pages;

import com.lili.ui.locators.LocatorRegistry;
import com.lili.ui.locators.RouteRegistry;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.Map;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** 积分商品详情页（/pointGoodsDetail?id=）。 */
public class PointGoodsDetailPage {

    private final Page page;
    private final Locator pageFlag;
    private final Locator buyButton;

    public PointGoodsDetailPage(Page page) {
        this.page = page;
        this.pageFlag = LocatorRegistry.resolve(page, "point_goods.page_flag");
        this.buyButton = LocatorRegistry.resolve(page, "point_goods.buy_button");
    }

    public PointGoodsDetailPage openById(String id) {
        RouteRegistry.open(page, "point_goods_detail", Map.of("id", id));
        assertThat(pageFlag.first()).isVisible();
        return this;
    }

    public PointGoodsDetailPage buyWithPoints() {
        buyButton.first().click();
        return this;
    }
}
