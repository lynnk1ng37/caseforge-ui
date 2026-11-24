package com.lili.ui.support;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;

/**
 * 新标签页处理。
 *
 * <p>lilishop 买家端多个入口用 {@code target="_blank"} 或 {@code window.open} 打开
 * （实测：顶部购物车链接、商品列表条目、我的订单入口），点击后当前页 URL 不变，
 * 所以必须等待 popup 并把后续步骤切到新页面，否则会误判成「点击无反应」。
 */
public final class Tabs {

    private Tabs() {
    }

    /**
     * 执行点击并返回实际承载内容的页面。
     *
     * @return 弹出新标签页时返回新页面；未弹窗时返回原页面（同页跳转场景）
     */
    public static Page clickOpeningNewTab(Page page, Runnable action) {
        try {
            Page popup = page.waitForPopup(new Page.WaitForPopupOptions().setTimeout(5000), action);
            popup.waitForLoadState(LoadState.DOMCONTENTLOADED);
            return popup;
        } catch (PlaywrightException e) {
            return page;
        }
    }
}
