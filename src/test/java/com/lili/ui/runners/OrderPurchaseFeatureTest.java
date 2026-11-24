package com.lili.ui.runners;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 单独执行 {@code features/order_purchase.feature}（下单链路）。
 *
 * <p>在测试面板里点这个类即可只跑该 feature；命令行等价于
 * {@code mvn test -Dtest=OrderPurchaseFeatureTest}。
 */
public class OrderPurchaseFeatureTest {

    @Test
    @DisplayName("features/order_purchase.feature")
    void runFeature() {
        FeatureRunner.run("classpath:features/order_purchase.feature");
    }
}
