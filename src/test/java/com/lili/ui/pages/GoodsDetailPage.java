package com.lili.ui.pages;

import com.lili.ui.locators.LocatorRegistry;
import com.lili.ui.locators.RouteRegistry;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * 商品详情页页面对象。
 *
 * <p>职责：只做动作与元素就绪等待，不做业务结果断言。
 * 定位器来源：classpath:/locators/goods_detail.yaml
 */
public class GoodsDetailPage {

    private static final Pattern DIGITS = Pattern.compile("(\\d+)");

    private final Page page;
    private final Locator pageFlag;
    private final Locator addToCartButton;
    private final Locator buyNowButton;
    private final Locator addedTip;
    private final Locator loginModal;
    private final Locator loginModalTip;
    private final Locator title;
    private final Locator price;
    private final Locator skuRow;
    private final Locator stockBlock;
    private final Locator quantityInput;
    private final Locator remarksCount;
    private final Locator remarksTab;
    private final Locator typeTag;
    private final Locator toast;

    public GoodsDetailPage(Page page) {
        this.page = page;
        this.pageFlag = LocatorRegistry.resolve(page, "goods_detail.page_flag");
        this.addToCartButton = LocatorRegistry.resolve(page, "goods_detail.add_to_cart");
        this.buyNowButton = LocatorRegistry.resolve(page, "goods_detail.buy_now");
        this.addedTip = LocatorRegistry.resolve(page, "goods_detail.added_tip");
        this.loginModal = LocatorRegistry.resolve(page, "goods_detail.login_modal");
        this.loginModalTip = LocatorRegistry.resolve(page, "goods_detail.login_modal_tip");
        this.title = LocatorRegistry.resolve(page, "goods_detail.title");
        this.price = LocatorRegistry.resolve(page, "goods_detail.price");
        this.skuRow = LocatorRegistry.resolve(page, "goods_detail.sku_row");
        this.stockBlock = LocatorRegistry.resolve(page, "goods_detail.stock_block");
        this.quantityInput = LocatorRegistry.resolve(page, "goods_detail.quantity_input");
        this.remarksCount = LocatorRegistry.resolve(page, "goods_detail.remarks_count");
        this.remarksTab = LocatorRegistry.resolve(page, "goods_detail.remarks_tab");
        this.typeTag = LocatorRegistry.resolve(page, "goods_detail.type_tag");
        this.toast = LocatorRegistry.resolve(page, "common.toast");
    }

    public GoodsDetailPage open(String goodsId, String skuId) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("goodsId", goodsId);
        query.put("skuId", skuId);
        RouteRegistry.open(page, "goods_detail", query);
        // 以页面共有文案判断加载完成，避免不可购买商品（无加购按钮）误判
        assertThat(pageFlag.first()).isVisible();
        return this;
    }

    /** 打开详情但不要求「累计评价」可见（下架商品详情只有下架提示）。 */
    public GoodsDetailPage openRaw(String goodsId) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("goodsId", goodsId);
        RouteRegistry.open(page, "goods_detail", query);
        return this;
    }

    /** 下架提示文案（异步渲染，先等它出现）。 */
    public String offShelfTipText() {
        Locator tip = LocatorRegistry.resolve(page, "goods_detail.off_shelf_tip");
        try {
            tip.first().waitFor(new Locator.WaitForOptions().setTimeout(8000));
        } catch (com.microsoft.playwright.PlaywrightException ignored) {
            // 未出现时返回空串，由 steps 的断言给出结论
        }
        return tip.count() == 0 ? "" : tip.first().innerText().trim();
    }

    public GoodsDetailPage open(String goodsId) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("goodsId", goodsId);
        RouteRegistry.open(page, "goods_detail", query);
        assertThat(pageFlag.first()).isVisible();
        return this;
    }

    /** 只触发加购，不等待成功（用于未登录 / 不可购买等负向场景）。 */
    public GoodsDetailPage requestAddToCart() {
        addToCartButton.click();
        return this;
    }

    public GoodsDetailPage addToCart() {
        requestAddToCart();
        // 加购成功的确定性信号是跳转到 /shoppingCart 成功页，
        // 不依赖会消失的 toast，顺序错乱时失败信息也更明确。
        assertThat(page).hasURL(Pattern.compile("/shoppingCart"));
        assertThat(addedTip).isVisible();
        return this;
    }

    public GoodsDetailPage buyNow() {
        buyNowButton.click();
        assertThat(page).hasURL(Pattern.compile("/pay"));
        return this;
    }

    public GoodsDetailPage expectLoginModal() {
        assertThat(loginModal.first()).isVisible();
        return this;
    }

    public String loginModalText() {
        if (loginModal.count() == 0) {
            return "";
        }
        return loginModal.first().innerText().trim();
    }

    public boolean hasAddToCartEntry() {
        return addToCartButton.count() > 0;
    }

    public boolean hasBuyNowEntry() {
        return buyNowButton.count() > 0;
    }

    public boolean isPurchasable() {
        return addToCartButton.count() > 0;
    }

    public String titleText() {
        return title.count() == 0 ? "" : title.first().innerText().trim();
    }

    public String priceText() {
        return price.count() == 0 ? "" : price.first().innerText().trim();
    }

    public List<String> skuNames() {
        return skuRow.allInnerTexts().stream().map(String::trim).toList();
    }

    /** 按规格文案选择 SKU，例如「黑色」；点击后等价格刷新，避免读到上一个 SKU 的价格。 */
    public GoodsDetailPage selectSku(String specName) {
        String before = priceText();
        skuRow.filter(new Locator.FilterOptions().setHasText(specName)).first().click();
        assertThat(price.first()).isVisible();
        if (!before.isBlank()) {
            assertThat(price.first()).not().hasText(before);
        }
        return this;
    }

    /** 从「库存1983」里取数字，取不到返回 -1。 */
    public int stockCount() {
        if (stockBlock.count() == 0) {
            return -1;
        }
        Matcher matcher = DIGITS.matcher(stockBlock.first().innerText());
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    public String quantity() {
        return quantityInput.count() == 0 ? "" : quantityInput.first().inputValue();
    }

    /** 输入数量后回车并失焦，触发组件归一化（0/-1/超库存回退或截断）。 */
    public GoodsDetailPage setQuantity(String value) {
        Locator input = quantityInput.first();
        input.click();
        input.fill(value);
        input.press("Enter");
        page.keyboard().press("Tab");
        return this;
    }

    public String remarksCountText() {
        return remarksCount.count() == 0 ? "" : remarksCount.first().innerText().trim();
    }

    public GoodsDetailPage openRemarksTab() {
        remarksTab.first().click();
        return this;
    }

    public boolean hasTypeTag() {
        return typeTag.count() > 0 && typeTag.first().isVisible();
    }

    public String toastText() {
        return toast.count() == 0 ? "" : toast.first().innerText().trim();
    }

    /** 加购成功页（/shoppingCart）上的成功文案。 */
    public String addedTipText() {
        return addedTip.count() == 0 ? "" : addedTip.first().innerText().trim();
    }
}
