package com.lili.ui.runners;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 单独执行 {@code features/cart.feature}（购物车）。
 *
 * <p>在测试面板里点这个类即可只跑该 feature；命令行等价于
 * {@code mvn test -Dtest=CartFeatureTest}。
 */
public class CartFeatureTest {

    @Test
    @DisplayName("features/cart.feature")
    void runFeature() {
        FeatureRunner.run("classpath:features/cart.feature");
    }
}
