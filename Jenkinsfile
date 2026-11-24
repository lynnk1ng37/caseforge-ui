/*
 * UI 自动化在 Jenkins 上的执行与报告发布（第一阶段）。
 *
 * Jenkins 侧一次性配置：
 *   1. 安装 Allure Jenkins Plugin，并在「全局工具配置」里添加 Allure Commandline（命名随意）
 *   2. agent 机器装 Chrome（config.yaml 里 ui.channel=chrome），不依赖 Playwright 下载浏览器
 *   3. 在凭据管理里创建 lilishop-ui-username / lilishop-ui-password，或改成 agent 上的环境变量
 *   4. 被测环境对 agent 网络可达；agent 需要 JDK 17
 *
 * 报告阅读路径：构建页 → Allure Report → 用例 → 红色步骤（即失败的 BDD step）
 *   → 附件：失败截图 / 失败时 DOM / 浏览器控制台 / 失败的网络请求 / playwright trace
 *
 * 跨 job 汇总：所有 job 的 allure-results 都被归档，可下载后合并成一份报告：
 *   allure generate jobA/target/allure-results jobB/target/allure-results -o merged
 */
pipeline {
    agent any

    parameters {
        string(name: 'TAGS', defaultValue: '@p0', description: 'Cucumber 标签过滤，例如 @p0 或 @cart')
        choice(name: 'UI_ENV', choices: ['uat', 'sit', 'local'], description: '环境标识，写入 Allure 环境面板')
        string(name: 'BASE_URL', defaultValue: 'http://127.0.0.1', description: '被测站点地址')
        booleanParam(name: 'HEADLESS', defaultValue: true, description: '无头模式，CI 上必须为 true')
        choice(name: 'BROWSER_CHANNEL', choices: ['none', 'chrome'],
                description: 'none 用镜像内置 chromium（Linux arm64 只能选这个）；chrome 用系统安装的 Chrome')
    }

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '30'))
        timeout(time: 90, unit: 'MINUTES')
    }

    environment {
        // TestConfig 的优先级是 系统属性 > 环境变量 > config.yaml，
        // 因此这里覆盖地址就足够，不需要改配置文件
        LILISHOP_UI_BASE_URL  = "${params.BASE_URL}"
        LILISHOP_API_BASE_URL = "${params.BASE_URL}/api"
        LILISHOP_USERNAME     = credentials('lilishop-ui-username')
        LILISHOP_PASSWORD     = credentials('lilishop-ui-password')
        // 自愈记录的落盘位置，run_stability.py 与审查 Agent 都读这个文件
        UI_HEAL_LOG           = "${WORKSPACE}/ui_test_dwp_temp/heals.jsonl"
    }

    stages {
        stage('执行用例') {
            steps {
                sh '''
                    # 清掉上一轮的 trace 与自愈记录，避免 archiveArtifacts 归档到陈旧文件
                    rm -rf ui_test_dwp_temp/artifacts ui_test_dwp_temp/heals.jsonl

                    mvn -B clean test \
                        -Dcucumber.filter.tags="${TAGS}" \
                        -Dui.headless="${HEADLESS}" \
                        -Dui.env="${UI_ENV}" \
                        -Dui.channel="${BROWSER_CHANNEL}"
                '''
            }
        }
    }

    post {
        always {
            junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true

            // 原始 results 必须归档：跨 job 合并报告、事后重生成都靠它
            archiveArtifacts artifacts: 'ui_test_dwp_temp/artifacts/**, ui_test_dwp_temp/heals.jsonl, target/allure-results/**',
                             allowEmptyArchive: true

            // 发布报告；allure 插件会用本 job 的历史构建画出通过率趋势
            allure includeProperties: false,
                   results: [[path: 'target/allure-results']]
        }
    }
}
