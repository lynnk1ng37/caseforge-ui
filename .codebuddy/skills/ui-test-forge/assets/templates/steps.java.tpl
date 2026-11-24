// <模块> 步骤定义（用例层）。
//
// 职责：参数转换 + 调 page/flow + 业务断言。
// 分层红线（门禁 G6 会拦）：
//   - 禁止 page.locator / page.click / page.fill / page.navigate
//   - 禁止 LocatorRegistry.resolve（那是 page 层的事）
//   - 禁止 Thread.sleep / page.waitForTimeout
//   - 禁止硬编码 URL
package com.lili.ui.steps;

import com.lili.ui.pages.<Page>Page;
import com.lili.ui.support.ScenarioContext;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

public class <X>Steps {

    private final ScenarioContext ctx;

    public <X>Steps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    @When("<动作步骤文案，与 feature 完全一致>")
    public void doSomething() {
        new <Page>Page(ctx.page()).open().doSomething();
    }

    @Then("<断言步骤文案，与 feature 完全一致>")
    public void shouldSeeExpected() {
        String actual = new <Page>Page(ctx.page()).readValue();
        assertThat(actual)
                .as("<中文说明>, url=%s", ctx.currentUrl())
                .isEqualTo("<期望值>");
    }
}
