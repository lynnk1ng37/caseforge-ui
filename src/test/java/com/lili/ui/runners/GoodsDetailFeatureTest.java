package com.lili.ui.runners;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 单独执行 {@code features/goods_detail.feature}（商品详情）。
 *
 * <p>在测试面板里点这个类即可只跑该 feature；命令行等价于
 * {@code mvn test -Dtest=GoodsDetailFeatureTest}。
 */
public class GoodsDetailFeatureTest {

    @Test
    @DisplayName("features/goods_detail.feature")
    void runFeature() {
        FeatureRunner.run("classpath:features/goods_detail.feature");
    }
}
