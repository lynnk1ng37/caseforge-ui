package com.lili.ui.steps;

import com.lili.ui.config.TestConfig;
import com.lili.ui.pages.CheckoutPage;
import com.lili.ui.pages.CouponCenterPage;
import com.lili.ui.pages.CouponPage;
import com.lili.ui.pages.GoodsDetailPage;
import com.lili.ui.support.CartWriteRateLimiter;
import com.lili.ui.support.DbFixture;
import com.lili.ui.support.ScenarioContext;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商品详情与营销活动 步骤定义（用例层）。
 *
 * <p>覆盖人工用例：LLS-GOODS-001 / 002 / 004 / 006 / 007 / 015。
 *
 * <p>实测数据（2026-11-23）：紫色 SKU ￥3,799、黑色 SKU ￥3,899，两者库存不同。
 */
public class GoodsSteps {

    private final ScenarioContext ctx;
    private GoodsDetailPage detail;
    private int blackStock = -1;
    private int purpleStock = -1;

    public GoodsSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    @Given("买家打开测试商品详情页")
    public void openTestGoodsDetail() {
        detail = new GoodsDetailPage(ctx.page()).open(TestConfig.get().goodsId());
        assertThat(detail.titleText())
                .as("商品详情页未渲染标题, url=%s", ctx.currentUrl())
                .isNotEmpty();
    }

    @When("买家选择商品规格黑色")
    public void selectBlackSku() {
        detail.selectSku("黑色");
        blackStock = detail.stockCount();
        assertThat(detail.priceText())
                .as("切换黑色后价格未渲染, url=%s", ctx.currentUrl())
                .isNotEmpty();
    }

    @Then("商品价格应显示为 3899 元")
    public void priceIs3899() {
        assertThat(detail.priceText())
                .as("黑色 SKU 价格不符, url=%s", ctx.currentUrl())
                .contains("3,899.00");
    }

    @And("当前规格库存应大于 0")
    public void currentStockPositive() {
        assertThat(detail.stockCount())
                .as("当前规格库存未渲染或不大于 0, url=%s", ctx.currentUrl())
                .isGreaterThan(0);
    }

    @When("买家选择商品规格紫色")
    public void selectPurpleSku() {
        detail.selectSku("紫色");
        purpleStock = detail.stockCount();
        assertThat(detail.priceText())
                .as("切换紫色后价格未渲染, url=%s", ctx.currentUrl())
                .isNotEmpty();
    }

    @Then("商品价格应显示为 3799 元")
    public void priceIs3799() {
        assertThat(detail.priceText())
                .as("紫色 SKU 价格不符, url=%s", ctx.currentUrl())
                .contains("3,799.00");
    }

    @And("两个规格的库存数应不同")
    public void stockDiffersBetweenSkus() {
        assertThat(purpleStock)
                .as("两个规格库存相同(%d)，SKU 未联动, url=%s", purpleStock, ctx.currentUrl())
                .isNotEqualTo(blackStock);
    }

    @Then("商品标题应包含一加 OnePlus 9")
    public void titleContainsGoodsName() {
        assertThat(detail.titleText())
                .as("商品标题不符, url=%s", ctx.currentUrl())
                .contains(TestConfig.get().goodsName());
    }

    @And("商品详情应展示实物商品标识")
    public void typeTagVisible() {
        assertThat(detail.hasTypeTag())
                .as("未展示「实物商品」标识, url=%s", ctx.currentUrl())
                .isTrue();
    }

    @And("商品详情应展示规格紫色与黑色")
    public void skuNamesVisible() {
        List<String> names = detail.skuNames();
        assertThat(names)
                .as("规格列表缺少紫色/黑色, 实际=%s, url=%s", names, ctx.currentUrl())
                .contains("紫色", "黑色");
    }

    @And("商品详情应展示购买入口")
    public void purchaseEntriesVisible() {
        assertThat(detail.hasAddToCartEntry())
                .as("缺少「加入购物车」入口, url=%s", ctx.currentUrl())
                .isTrue();
        assertThat(detail.hasBuyNowEntry())
                .as("缺少「立即购买」入口, url=%s", ctx.currentUrl())
                .isTrue();
    }

    @And("商品详情应展示累计评价数量")
    public void remarksCountVisible() {
        assertThat(detail.remarksCountText())
                .as("未展示累计评价数量, url=%s", ctx.currentUrl())
                .isNotEmpty();
    }

    @When("买家把购买数量改为 0")
    public void setQuantityZero() {
        detail.setQuantity("0");
        assertThat(detail.quantity())
                .as("数量输入框未回填, url=%s", ctx.currentUrl())
                .isNotEmpty();
    }

    @Then("购买数量应回退为 1")
    public void quantityFallsBackToOne() {
        assertThat(detail.quantity())
                .as("非法数量未回退为 1, url=%s", ctx.currentUrl())
                .isEqualTo("1");
    }

    @When("买家把购买数量改为 -1")
    public void setQuantityNegative() {
        detail.setQuantity("-1");
        assertThat(detail.quantity())
                .as("数量输入框未回填, url=%s", ctx.currentUrl())
                .isNotEmpty();
    }

    @When("买家把购买数量改为 99999")
    public void setQuantityOverStock() {
        detail.setQuantity("99999");
        assertThat(detail.quantity())
                .as("数量输入框未回填, url=%s", ctx.currentUrl())
                .isNotEmpty();
    }

    @Then("购买数量应被截断为库存上限")
    public void quantityClampedToStock() {
        int stock = detail.stockCount();
        assertThat(Integer.parseInt(detail.quantity()))
                .as("超库存数量未被截断, 数量=%s 库存=%d, url=%s", detail.quantity(), stock, ctx.currentUrl())
                .isEqualTo(stock);
    }

    @When("买家点击加入购物车")
    public void addToCart() {
        for (int attempt = 0; attempt < 2; attempt++) {
            CartWriteRateLimiter.awaitSlot();
            detail.addToCart();
            if (ctx.currentUrl().contains("/shoppingCart")) {
                break;
            }
        }
        assertThat(ctx.currentUrl())
                .as("加购后未进入成功页, url=%s", ctx.currentUrl())
                .contains("/shoppingCart");
    }

    @Then("应提示商品已成功加入购物车")
    public void addedTipVisible() {
        assertThat(detail.addedTipText())
                .as("加购成功页未提示成功, url=%s", ctx.currentUrl())
                .contains("商品已成功加入购物车");
    }

    @When("买家在商品详情页点击立即购买")
    public void buyNow() {
        detail = new GoodsDetailPage(ctx.page()).open(TestConfig.get().goodsId());
        detail.buyNow();
        assertThat(ctx.currentUrl())
                .as("立即购买后未进入结算页, url=%s", ctx.currentUrl())
                .contains("/pay");
    }

    @Then("应进入结算页并展示当前商品")
    public void checkoutShowsGoods() {
        CheckoutPage checkout = new CheckoutPage(ctx.page()).expectLoaded();
        assertThat(checkout.goodsItemText())
                .as("结算页未展示当前商品, url=%s", ctx.currentUrl())
                .contains(TestConfig.get().goodsName());
    }

    @Given("买家当前持有 3 张优惠券")
    public void couponsHeld() {
        int count = new CouponPage(ctx.page()).open().itemCount();
        assertThat(count)
                .as("账号持有券数量不是 3, 实际=%d, url=%s", count, ctx.currentUrl())
                .isEqualTo(3);
    }

    @When("买家在领券中心点击第一张券的立即领取")
    public void receiveFirstCoupon() {
        CouponCenterPage center = new CouponCenterPage(ctx.page()).open();
        assertThat(center.receiveButtonCount())
                .as("领券中心没有可领取的券, url=%s", ctx.currentUrl())
                .isGreaterThan(0);
        center.receiveFirst();
    }

    @Then("买家的优惠券数量应仍为 3 张")
    public void couponCountUnchanged() {
        int count = new CouponPage(ctx.page()).open().itemCount();
        assertThat(count)
                .as("重复领取后券数量变化, 实际=%d, url=%s", count, ctx.currentUrl())
                .isEqualTo(3);
    }

    @When("买家打开一个已下架商品的详情页")
    public void openOffShelfGoods() {
        // 造数：把主商品置为下架，场景结束后由 DataHooks 还原
        DbFixture.setGoodsOffShelf(TestConfig.get().offShelfGoodsId());
        detail = new GoodsDetailPage(ctx.page()).openRaw(TestConfig.get().offShelfGoodsId());
        assertThat(ctx.currentUrl())
                .as("未进入商品详情路由, url=%s", ctx.currentUrl())
                .contains("/goodsDetail");
    }

    @Then("应提示当前商品已下架")
    public void offShelfTipShown() {
        assertThat(detail.offShelfTipText())
                .as("未展示下架提示, url=%s", ctx.currentUrl())
                .contains("当前商品已下架");
    }

    @And("不应展示加入购物车与立即购买入口")
    public void noPurchaseEntry() {
        assertThat(detail.hasAddToCartEntry())
                .as("下架商品仍展示加入购物车, url=%s", ctx.currentUrl())
                .isFalse();
        assertThat(detail.hasBuyNowEntry())
                .as("下架商品仍展示立即购买, url=%s", ctx.currentUrl())
                .isFalse();
    }

    @Then("累计评价数量应显示为 0 条")
    public void remarksCountIsZero() {
        assertThat(detail.remarksCountText())
                .as("累计评价数量不符, url=%s", ctx.currentUrl())
                .contains("0 条");
    }

    @When("买家切换到商品评价页签")
    public void openRemarksTab() {
        detail.openRemarksTab();
        assertThat(ctx.currentUrl())
                .as("切换评价页签后离开了详情页, url=%s", ctx.currentUrl())
                .contains("/goodsDetail");
    }

    @Then("商品评价不应出现错误提示")
    public void remarksNoError() {
        assertThat(detail.toastText())
                .as("评价区出现错误提示, url=%s", ctx.currentUrl())
                .doesNotContain("错误")
                .doesNotContain("失败");
    }
}
