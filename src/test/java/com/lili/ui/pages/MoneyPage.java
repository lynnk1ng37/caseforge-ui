package com.lili.ui.pages;

import com.lili.ui.locators.LocatorRegistry;
import com.lili.ui.locators.RouteRegistry;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** 资金管理页（/home/MoneyManagement）。 */
public class MoneyPage {

    private static final Pattern AMOUNT = Pattern.compile("￥\\s*([\\d,]+\\.\\d{2})");

    private final Page page;
    private final Locator balance;

    public MoneyPage(Page page) {
        this.page = page;
        this.balance = LocatorRegistry.resolve(page, "asset.money_balance");
    }

    public MoneyPage open() {
        RouteRegistry.open(page, "money_management");
        assertThat(balance.first()).isVisible();
        return this;
    }

    public String balanceText() {
        return balance.first().innerText().trim();
    }

    public double balanceAmount() {
        Matcher matcher = AMOUNT.matcher(balanceText());
        return matcher.find() ? Double.parseDouble(matcher.group(1).replace(",", "")) : -1d;
    }
}
