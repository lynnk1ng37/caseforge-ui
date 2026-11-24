package com.lili.ui.runners;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 单独执行 {@code features/order_center.feature}（订单中心）。
 *
 * <p>在测试面板里点这个类即可只跑该 feature；命令行等价于
 * {@code mvn test -Dtest=OrderCenterFeatureTest}。
 */
public class OrderCenterFeatureTest {

    @Test
    @DisplayName("features/order_center.feature")
    void runFeature() {
        FeatureRunner.run("classpath:features/order_center.feature");
    }
}
