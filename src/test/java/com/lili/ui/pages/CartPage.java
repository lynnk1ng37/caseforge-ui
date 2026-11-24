package com.lili.ui.pages;

import com.lili.ui.locators.LocatorRegistry;
import com.lili.ui.locators.RouteRegistry;
import com.lili.ui.support.CartWriteRateLimiter;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * 购物车页页面对象。
 *
 * <p>职责：读取条目数 / 数量 / 总价 / 角标，改数量、勾选、删除、清空；
 * 不做业务断言（金额是否正确、是否拦截由 steps 判定）。
 *
 * <p>定位器来源：classpath:/locators/cart.yaml
 */
public class CartPage {

    private final Page page;
    private final Locator items;
    private final Locator emptyTip;
    private final Locator total;
    private final Locator checkoutButton;
    private final Locator itemName;
    private final Locator quantityInput;
    private final Locator quantityPlus;
    private final Locator quantityMinus;
    private final Locator rowCheckbox;
    private final Locator rowChecked;
    private final Locator selectAll;
    private final Locator itemDelete;
    private final Locator clearAll;
    private final Locator clearConfirm;
    private final Locator clearCancel;
    private final Locator badge;
    private final Locator itemPrice;
    private final Locator itemSubtotal;

    public CartPage(Page page) {
        this.page = page;
        this.items = LocatorRegistry.resolve(page, "cart.items");
        this.emptyTip = LocatorRegistry.resolve(page, "cart.empty_tip");
        this.total = LocatorRegistry.resolve(page, "cart.total_amount");
        this.checkoutButton = LocatorRegistry.resolve(page, "cart.checkout");
        this.itemName = LocatorRegistry.resolve(page, "cart.item_name");
        this.quantityInput = LocatorRegistry.resolve(page, "cart.quantity_input");
        this.quantityPlus = LocatorRegistry.resolve(page, "cart.quantity_plus");
        this.quantityMinus = LocatorRegistry.resolve(page, "cart.quantity_minus");
        this.rowCheckbox = LocatorRegistry.resolve(page, "cart.row_checkbox");
        this.rowChecked = LocatorRegistry.resolve(page, "cart.row_checked");
        this.selectAll = LocatorRegistry.resolve(page, "cart.select_all");
        this.itemDelete = LocatorRegistry.resolve(page, "cart.item_delete");
        this.clearAll = LocatorRegistry.resolve(page, "cart.clear_all");
        this.clearConfirm = LocatorRegistry.resolve(page, "cart.clear_confirm");
        this.clearCancel = LocatorRegistry.resolve(page, "cart.clear_cancel");
        this.badge = LocatorRegistry.resolve(page, "cart.badge");
        this.itemPrice = LocatorRegistry.resolve(page, "cart.item_price");
        this.itemSubtotal = LocatorRegistry.resolve(page, "cart.item_subtotal");
    }

    public CartPage open() {
        RouteRegistry.open(page, "cart");
        // 有商品时等条目出现；空车时等空态出现。两者都未命中时交给调用方的业务断言暴露，
        // 这里只做有限等待，避免把「空车渲染慢」直接判成定位断链。
        try {
            items.first().or(emptyTip.first()).waitFor(new Locator.WaitForOptions().setTimeout(8000));
        } catch (com.microsoft.playwright.PlaywrightException ignored) {
            // 空态 DOM 版本差异：由 steps 的条数/金额断言给出结论
        }
        return this;
    }

    /** 重新加载购物车，用于验证服务端最新数据（不依赖固定等待）。 */
    public CartPage reload() {
        page.reload(new Page.ReloadOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        assertThat(items.first().or(emptyTip.first())).isVisible();
        return this;
    }

    /** 店铺分组数（每个分组头部有「去店铺」入口）。 */
    public int storeGroupCount() {
        Locator stores = LocatorRegistry.resolve(page, "cart.store_name");
        return stores.count();
    }

    /** 点击表头全选框。 */
    public CartPage toggleSelectAllInHeader() {
        Locator checkbox = LocatorRegistry.resolve(page, "cart.select_all_title");
        checkbox.first().click();
        return this;
    }

    /** 表头全选框是否处于已全选状态。 */
    public boolean selectAllChecked() {
        Locator checked = LocatorRegistry.resolve(page, "cart.select_all_checked");
        return checked.count() > 0;
    }

    public boolean indeterminateVisible() {
        Locator indeterminate = LocatorRegistry.resolve(page, "cart.indeterminate");
        return indeterminate.count() > 0 && indeterminate.first().isVisible();
    }

    public int itemCount() {
        return items.count();
    }

    /** 「已选择 N 件商品」文案。 */
    public String selectedCountText() {
        Locator selected = LocatorRegistry.resolve(page, "cart.selected_count");
        return selected.count() == 0 ? "" : selected.first().innerText().trim();
    }

    public boolean isEmpty() {
        return emptyTip.count() > 0 && emptyTip.first().isVisible();
    }

    public String totalText() {
        return total.count() == 0 ? "" : total.first().innerText().trim();
    }

    /** 把「￥3,799.00」解析为 3799.00，便于数值比较。 */
    public double totalAmount() {
        return parseAmount(totalText());
    }

    public double itemPriceAmount() {
        return itemPrice.count() == 0 ? 0d : parseAmount(itemPrice.first().innerText());
    }

    /** 等待小计文本刷新到指定金额（服务端重算有延迟，直接取值会读到上一态）。 */
    public CartPage awaitSubtotalText(String expectedText) {
        if (itemSubtotal.count() > 0) {
            assertThat(itemSubtotal.first()).containsText(expectedText);
        }
        return this;
    }

    /** 等待已勾选行数变为指定值（勾选状态更新是同页异步渲染）。 */
    public CartPage awaitCheckedRows(int expected) {
        assertThat(rowChecked).hasCount(expected);
        return this;
    }

    /** 等待行数变为指定值（删除是异步请求，直接 count 会读到删除前）。 */
    public CartPage awaitRowCount(int expected) {
        assertThat(items).hasCount(expected);
        return this;
    }

    /** 等待总价文本不再是旧值（勾选重算走异步，直接读会读到上一态）。 */
    public CartPage awaitTotalChangedFrom(String oldText) {
        if (total.count() > 0 && oldText != null && !oldText.isBlank()) {
            assertThat(total.first()).not().containsText(oldText);
        }
        return this;
    }

    public double itemSubtotalAmount() {
        return itemSubtotal.count() == 0 ? 0d : parseAmount(itemSubtotal.first().innerText());
    }

    /** 所有行文本，用于校验商品名。 */
    public java.util.List<String> itemTexts() {
        return items.allInnerTexts().stream().map(String::trim).toList();
    }

    public String firstItemText() {
        return items.count() == 0 ? "" : items.first().innerText().trim();
    }

    public String firstName() {
        return itemName.count() == 0 ? "" : itemName.first().innerText().trim();
    }

    public String quantity() {
        return quantityInput.count() == 0 ? "" : quantityInput.first().inputValue();
    }

    /** 输入数量后回车并失焦，触发组件归一化与服务端重算。 */
    public CartPage setQuantity(String value) {
        // 购物车写接口同样受短窗口限流（code=1003），连续改数量会静默失败
        CartWriteRateLimiter.awaitSlot();
        Locator input = quantityInput.first();
        input.click();
        input.fill(value);
        input.press("Enter");
        page.keyboard().press("Tab");
        return this;
    }

    public CartPage clickPlus() {
        CartWriteRateLimiter.awaitSlot();
        quantityPlus.first().click();
        return this;
    }

    public CartPage clickMinus() {
        CartWriteRateLimiter.awaitSlot();
        quantityMinus.first().click();
        return this;
    }

    public boolean minusDisabled() {
        return quantityMinus.count() > 0
                && quantityMinus.first().getAttribute("class").contains("disabled");
    }

    public boolean plusDisabled() {
        return quantityPlus.count() > 0
                && quantityPlus.first().getAttribute("class").contains("disabled");
    }

    public int rowCheckboxCount() {
        return rowCheckbox.count();
    }

    public int selectedRowCount() {
        return rowChecked.count();
    }

    public CartPage toggleRow(int index) {
        rowCheckbox.nth(index).click();
        return this;
    }

    public CartPage clickSelectAll() {
        selectAll.first().click();
        return this;
    }

    /** 删除第一行；若弹确认框则点确定（弹框是异步渲染，需要等它出现）。 */
    public CartPage deleteFirst() {
        CartWriteRateLimiter.awaitSlot();
        itemDelete.first().click();
        Locator ok = LocatorRegistry.resolve(page, "cart.confirm_ok");
        try {
            // click 自带等待：弹框异步渲染，等到可操作再点；不弹框的版本会超时后跳过
            ok.first().click(new Locator.ClickOptions().setTimeout(6000));
        } catch (com.microsoft.playwright.PlaywrightException ignored) {
            // 部分版本删除不弹确认框，直接生效
        }
        return this;
    }

    /** 点「清空购物车」后在确认弹窗点取消。 */
    public CartPage clearAllAndCancel() {
        CartWriteRateLimiter.awaitSlot();
        clearAll.first().click();
        assertThat(clearConfirm.first()).isVisible();
        clearCancel.first().click();
        return this;
    }

    public String badgeText() {
        return badge.count() == 0 ? "" : badge.first().innerText().trim();
    }

    /** 结算步骤条文案（购物车页独有「1.我的购物车」）。 */
    public String stepTitleText() {
        Locator stepTitle = LocatorRegistry.resolve(page, "cart.step_title");
        return stepTitle.count() == 0 ? "" : stepTitle.first().innerText().trim();
    }

    /**
     * 未登录直接访问购物车，等待被重定向到登录页。
     *
     * @return true=已跳到登录页；false=超时未跳转
     */
    public boolean openAndAwaitLoginRedirect() {
        RouteRegistry.open(page, "cart");
        try {
            page.waitForURL(url -> url.contains("/login"),
                    new Page.WaitForURLOptions()
                            .setTimeout(5000)
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            return true;
        } catch (com.microsoft.playwright.PlaywrightException e) {
            return false;
        }
    }

    public CartPage goCheckout() {
        checkoutButton.click();
        return this;
    }

    private static double parseAmount(String text) {
        String digits = text.replaceAll("[^\\d.]", "");
        return digits.isEmpty() ? 0d : Double.parseDouble(digits);
    }
}
