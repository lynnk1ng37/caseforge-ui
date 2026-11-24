// <页面名>页面对象。
//
// 职责：动作 + 就绪等待 + 读值，不做业务结果断言。
// 定位器来源：src/test/resources/locators/<page>.yaml
// 分层红线：不得出现 import org.assertj / org.junit（门禁 G7 会拦）。
package com.lili.ui.pages;

import com.lili.ui.locators.LocatorRegistry;
import com.lili.ui.locators.RouteRegistry;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public class <Page>Page {

    private final Page page;
    private final Locator someElement;

    public <Page>Page(Page page) {
        this.page = page;
        this.someElement = LocatorRegistry.resolve(page, "<page>.some_element");
    }

    public <Page>Page open() {
        RouteRegistry.open(page, "<route_key>");
        // 就绪等待：等到"下一步需要的前置元素"
        assertThat(someElement).isVisible();
        return this;
    }

    public <Page>Page doSomething() {
        someElement.click();
        return this;
    }

    public String readValue() {
        return someElement.innerText().trim();
    }
}
