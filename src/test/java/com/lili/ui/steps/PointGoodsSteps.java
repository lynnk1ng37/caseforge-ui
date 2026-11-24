package com.lili.ui.steps;

import com.lili.ui.pages.PointGoodsDetailPage;
import com.lili.ui.support.ScenarioContext;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 积分商品 步骤定义（用例层）。
 *
 * <p>覆盖人工用例：LLS-GOODS-014 / LLS-ASSET-010（积分不足分支）。
 * 实测（2026-11-24）：账号 10 积分、商品需 500 积分，点「积分购买」静默无反馈，
 * 既无提示也不跳转（已作为缺陷记录），因此断言锁定「不生成订单、不进入结算」。
 */
public class PointGoodsSteps {

    private static final String POINT_GOODS_ID = "2115700000004000";

    private final ScenarioContext ctx;
    private int pendingBefore = -1;

    public PointGoodsSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    @When("买家在积分商品详情页点击积分购买")
    public void buyPointGoods() {
        pendingBefore = ctx.api().pageOrders("WAIT_PAY").size();
        new PointGoodsDetailPage(ctx.page()).openById(POINT_GOODS_ID).buyWithPoints();
        assertThat(ctx.currentUrl())
                .as("积分购买后页面异常, url=%s", ctx.currentUrl())
                .contains("/pointGoodsDetail");
    }

    @Then("积分不足应被拦截且不生成订单")
    public void pointsNotEnoughBlocked() {
        assertThat(ctx.currentUrl())
                .as("积分不足却进入了结算页, url=%s", ctx.currentUrl())
                .doesNotContain("/pay");
        int after = ctx.api().pageOrders("WAIT_PAY").size();
        assertThat(after)
                .as("积分不足仍生成了订单, 之前=%d 之后=%d, url=%s", pendingBefore, after, ctx.currentUrl())
                .isEqualTo(pendingBefore);
    }
}
