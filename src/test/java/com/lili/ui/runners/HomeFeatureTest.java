package com.lili.ui.runners;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 单独执行 {@code features/home.feature}（首页）。
 *
 * <p>在测试面板里点这个类即可只跑该 feature；命令行等价于
 * {@code mvn test -Dtest=HomeFeatureTest}。
 */
public class HomeFeatureTest {

    @Test
    @DisplayName("features/home.feature")
    void runFeature() {
        FeatureRunner.run("classpath:features/home.feature");
    }
}
