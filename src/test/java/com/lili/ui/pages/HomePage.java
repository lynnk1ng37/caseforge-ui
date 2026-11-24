package com.lili.ui.pages;

import com.lili.ui.locators.LocatorRegistry;
import com.lili.ui.locators.RouteRegistry;
import com.lili.ui.support.Tabs;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitUntilState;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * 首页页面对象。
 *
 * <p>职责：打开首页、提交搜索、点击顶部入口，并读取分类/推荐位数量。
 * 不做业务断言（是否命中、数量是否达标由 steps 判定）。
 *
 * <p>定位器来源：classpath:/locators/{home,common}.yaml
 */
public class HomePage {

    private final Page page;
    private final Locator searchInput;
    private final Locator categoryItem;
    private final Locator recommendItem;
    private final Locator navCart;
    private final Locator navOrders;
    private final Locator navLogin;
    private final Locator navRegister;
    private final Locator navCouponEntry;
    private final Locator navSeckillEntry;
    private final Locator seckillFlag;

    public HomePage(Page page) {
        this.page = page;
        this.searchInput = LocatorRegistry.resolve(page, "home.search_input");
        this.categoryItem = LocatorRegistry.resolve(page, "home.category_item");
        this.recommendItem = LocatorRegistry.resolve(page, "home.recommend_item");
        this.navCart = LocatorRegistry.resolve(page, "common.nav_cart");
        this.navOrders = LocatorRegistry.resolve(page, "common.nav_orders");
        this.navLogin = LocatorRegistry.resolve(page, "common.nav_login");
        this.navRegister = LocatorRegistry.resolve(page, "common.nav_register");
        this.navCouponEntry = LocatorRegistry.resolve(page, "common.nav_coupon_entry");
        this.navSeckillEntry = LocatorRegistry.resolve(page, "common.nav_seckill_entry");
        this.seckillFlag = LocatorRegistry.resolve(page, "home.seckill_flag");
    }

    public HomePage open() {
        RouteRegistry.openIfNeeded(page, "home");
        assertThat(searchInput.first()).isVisible();
        // 分类导航由接口渲染完成后才出现，等它出现可避免「Vue 未挂载时回车走表单默认提交」
        assertThat(categoryItem).not().hasCount(0);
        return this;
    }

    /** 清空 cookie 后打开首页，用于未登录场景。 */
    public HomePage openAsGuest() {
        page.context().clearCookies();
        return open();
    }

    /** 只把内容写进搜索框，不提交（长度边界用）。 */
    public HomePage fillSearchOnly(String keyword) {
        searchInput.first().fill(keyword);
        return this;
    }

    /** 只提交搜索，不等待跳转（空关键字等负向场景用）。 */
    public HomePage submitSearch(String keyword) {
        searchInput.first().fill(keyword);
        searchInput.first().press("Enter");
        return this;
    }

    /**
     * 提交搜索并等待进入结果页。
     *
     * @return true=已跳转结果页；false=超时未跳转（空关键字场景）
     */
    public boolean submitSearchAndAwaitResults(String keyword) {
        submitSearch(keyword);
        try {
            page.waitForURL(url -> url.contains("goodsList"),
                    new Page.WaitForURLOptions()
                            .setTimeout(5000)
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            return true;
        } catch (PlaywrightException e) {
            return false;
        }
    }

    public String url() {
        return page.url();
    }

    /** 顶部「我的订单」入口文案。 */
    public String navOrdersText() {
        return navOrders.count() == 0 ? "" : navOrders.first().innerText().trim();
    }

    /** 顶部「秒杀」入口文案。 */
    public String navSeckillText() {
        return navSeckillEntry.count() == 0 ? "" : navSeckillEntry.first().innerText().trim();
    }

    /** 顶部「优惠券」入口文案。 */
    public String navCouponText() {
        return navCouponEntry.count() == 0 ? "" : navCouponEntry.first().innerText().trim();
    }

    /** 顶部购物车角标文案，形如「购物车（1）」。 */
    public String cartBadgeText() {
        return navCart.count() == 0 ? "" : navCart.first().innerText().trim();
    }

    public String searchInputValue() {
        return searchInput.first().inputValue();
    }

    public int categoryCount() {
        return categoryItem.count();
    }

    public int recommendCount() {
        return recommendItem.count();
    }

    public boolean loginEntryVisible() {
        return navLogin.count() > 0 && navLogin.first().isVisible();
    }

    public boolean registerEntryVisible() {
        return navRegister.count() > 0 && navRegister.first().isVisible();
    }

    /** 顶部购物车入口以新标签页打开，返回实际承载页面的 Page。 */
    public Page clickCartEntry() {
        Page target = Tabs.clickOpeningNewTab(page, () -> navCart.first().click());
        assertThat(target).hasURL(Pattern.compile("/cart"));
        return target;
    }

    public Page clickOrdersEntry() {
        Page target = Tabs.clickOpeningNewTab(page, () -> navOrders.first().click());
        assertThat(target).hasURL(Pattern.compile("/home/MyOrder"));
        return target;
    }

    public Page clickCouponEntry() {
        Page target = Tabs.clickOpeningNewTab(page, () -> navCouponEntry.first().click());
        assertThat(target).hasURL(Pattern.compile("/coupon"));
        return target;
    }

    public Page clickSeckillEntry() {
        Page target = Tabs.clickOpeningNewTab(page, () -> navSeckillEntry.first().click());
        assertThat(target).hasURL(Pattern.compile("/seckill"));
        return target;
    }

    public boolean seckillPageVisible() {
        try {
            seckillFlag.first().waitFor(new Locator.WaitForOptions().setTimeout(8000));
        } catch (com.microsoft.playwright.PlaywrightException ignored) {
            return false;
        }
        return seckillFlag.count() > 0 && seckillFlag.first().isVisible();
    }
}
