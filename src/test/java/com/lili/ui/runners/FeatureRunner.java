package com.lili.ui.runners;

import io.cucumber.core.cli.Main;
import org.assertj.core.api.Assertions;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个 feature 文件的执行入口。
 *
 * <p>为什么需要它：IDE 的测试运行器只认 JUnit 测试类，{@code .feature} 文件不是可执行单元，
 * 右键必然提示「找不到测试」。这里用 Cucumber 官方命令行入口 {@link Main} 按文件精确执行，
 * 再由 {@code com.lili.ui.runners} 下的各个测试类包装，于是「一个 feature 文件 = 一个可在
 * 测试面板里点运行的测试」。
 *
 * <p>glue 与报告插件来自 {@code src/test/resources/cucumber.properties}，
 * 与 {@code RunCucumberTest} 保持一致。
 */
public final class FeatureRunner {

    private FeatureRunner() {
    }

    /**
     * 执行单个 feature 文件，失败时抛断言错误（便于 IDE 直接标红，而不是静默 System.exit）。
     *
     * @param featureUri 类路径形式的 feature，例如 {@code classpath:features/cart.feature}
     */
    public static void run(String featureUri) {
        List<String> argv = new ArrayList<>();
        argv.add(featureUri);

        String tags = System.getProperty("cucumber.filter.tags");
        if (tags == null || tags.isBlank()) {
            // 空字符串不是合法的 tag 表达式：mvn test 会把该属性以空串注入，这里先清掉
            System.clearProperty("cucumber.filter.tags");
        } else {
            argv.add("--tags");
            argv.add(tags.trim());
        }

        byte exitStatus = Main.run(argv.toArray(new String[0]),
                Thread.currentThread().getContextClassLoader());

        Assertions.assertThat(exitStatus)
                .as("feature 执行结果：%s", featureUri)
                .isZero();
    }
}
