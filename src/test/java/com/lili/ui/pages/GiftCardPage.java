package com.lili.ui.pages;

import com.lili.ui.locators.LocatorRegistry;
import com.lili.ui.locators.RouteRegistry;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** 我的礼品卡页（/home/MyGiftCards）。 */
public class GiftCardPage {

    private final Page page;
    private final Locator cardNo;
    private final Locator redeemCode;
    private final Locator redeemButton;
    private final Locator toast;
    private final Locator cards;

    public GiftCardPage(Page page) {
        this.page = page;
        this.cardNo = LocatorRegistry.resolve(page, "asset.gift_card_no");
        this.redeemCode = LocatorRegistry.resolve(page, "asset.gift_card_code");
        this.redeemButton = LocatorRegistry.resolve(page, "asset.gift_card_redeem");
        this.toast = LocatorRegistry.resolve(page, "common.toast");
        this.cards = LocatorRegistry.resolve(page, "coupon.my_item");
    }

    public GiftCardPage open() {
        RouteRegistry.open(page, "my_gift_cards");
        assertThat(redeemButton.first()).isVisible();
        return this;
    }

    public GiftCardPage redeem(String no, String code) {
        cardNo.first().fill(no);
        redeemCode.first().fill(code);
        redeemButton.first().click();
        return this;
    }

    /** 等待并读取 toast 文案（异步弹出，先等出现）。 */
    public String toastText() {
        try {
            toast.first().waitFor(new Locator.WaitForOptions().setTimeout(6000));
        } catch (com.microsoft.playwright.PlaywrightException ignored) {
            return "";
        }
        return toast.first().innerText().trim();
    }

    public int cardCount() {
        return cards.count();
    }

    /** 页面标识：礼品卡页标题。 */
    public boolean pageReady() {
        return page.url().contains("/home/MyGiftCards")
                && redeemButton.count() > 0
                && Pattern.compile("兑换").matcher(redeemButton.first().innerText()).find();
    }
}
