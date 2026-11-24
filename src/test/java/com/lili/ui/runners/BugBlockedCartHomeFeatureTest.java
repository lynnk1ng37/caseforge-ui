package com.lili.ui.runners;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 单独执行 {@code features/bug_blocked_cart_home.feature}（受阻用例的等价断言）。
 *
 * <p>在测试面板里点这个类即可只跑该 feature；命令行等价于
 * {@code mvn test -Dtest=BugBlockedCartHomeFeatureTest}。
 */
public class BugBlockedCartHomeFeatureTest {

    @Test
    @DisplayName("features/bug_blocked_cart_home.feature")
    void runFeature() {
        FeatureRunner.run("classpath:features/bug_blocked_cart_home.feature");
    }
}
