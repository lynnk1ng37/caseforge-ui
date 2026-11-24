package com.lili.ui.pages;

import com.lili.ui.locators.LocatorRegistry;
import com.lili.ui.locators.RouteRegistry;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** 我的等级页（/home/MemberGrade）。 */
public class GradePage {

    private final Page page;
    private final Locator card;

    public GradePage(Page page) {
        this.page = page;
        this.card = LocatorRegistry.resolve(page, "asset.grade_card");
    }

    public GradePage open() {
        RouteRegistry.open(page, "my_grade");
        assertThat(card.first()).isVisible();
        return this;
    }

    public String cardText() {
        return card.first().innerText().replaceAll("\\s+", " ").trim();
    }
}
