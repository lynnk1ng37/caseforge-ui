# Jenkins（单容器）运行说明

本目录是 Jenkins 控制器的构建上下文：Playwright 1.49.0 官方镜像 + JDK + Maven + Allure CLI，
一个容器里跑 Jenkins，用 `java -jar jenkins.war` 启动，不依赖 `jenkins/jenkins` 镜像。

## 为什么这样搭

| 选择 | 原因 |
|---|---|
| `mcr.microsoft.com/playwright:v1.49.0-jammy` | tag 与 pom 的 Playwright 1.49.0 对齐，`/ms-playwright` 下的 chromium 版本才匹配；Linux arm64 没有 Google Chrome 官方包 |
| JDK 21（不是 17） | Jenkins 2.580.2 的 class 文件版本是 65，JDK 17 启动即 `UnsupportedClassVersionError`。项目仍按 pom 的 release=17 编译 |
| 容器内 `127.0.0.1:80` → 被测站点（socat） | 被测前端的 `/config.js` 把 API 写死成 `http://127.0.0.1:80/api`，浏览器在容器里跑时 127.0.0.1 不是被测系统 |
| 固定网络 `lilishop-net` | 默认 bridge 不支持容器名 DNS；`docker network connect` 热挂到被测容器，不重建、不停业务 |
| 容器内装 docker CLI + 挂宿主 `docker.sock` | `DbFixture` 用 `docker exec lilishop-all mysql` 造数（库存/上下架/余额），测试进程里直接调 docker |
| 80 端口作为 BASE_URL | 被测系统 80 是买家端（200），8080 是后端 API（根路径 404） |

## 构建 / 启动

```bash
cd /Users/lynnking/CodeBuddy/UI-TEST-AI-FOR-JAVA
docker build -t jenkins-ui-test:1.2 jenkins/

docker rm -f jenkins 2>/dev/null
docker run -d --name jenkins \
  -p 127.0.0.1:8082:8080 \
  -v jenkins_home:/var/jenkins_home \
  -v jenkins_m2:/root/.m2/repository \
  -v /Users/lynnking/CodeBuddy/UI-TEST-AI-FOR-JAVA:/src/UI-TEST-AI-FOR-JAVA:ro \
  -v /Users/lynnking/.docker/run/docker.sock:/var/run/docker.sock \
  --shm-size=1g \
  --add-host host.docker.internal:host-gateway \
  --network lilishop-net \
  --restart unless-stopped \
  -e LILISHOP_REDIS_HOST=lilishop-all \
  -e LILISHOP_REDIS_PORT=6379 \
  -e LILISHOP_REDIS_PASSWORD=lilishop \
  jenkins-ui-test:1.2
```

- 想对局域网开放：把 `-p 127.0.0.1:8082:8080` 改成 `-p 8082:8080`（当前只绑本机）。
- 宿主 8080 被被测系统的 API 占用，8081 被别的 java 进程占用，所以端口用 **8082**。

## 首次配置都做了什么（可重放）

1. 管理员：初始密码在 `docker logs jenkins` 里（`/var/jenkins_home/secrets/initialAdminPassword` 同源），
   通过 `/setupWizard/createAdminUser` + `/setupWizard/completeInstall` 完成向导。
   现在的账号 `admin`，密码在本机 `~/.jenkins-ui-test/admin.env`（chmod 600，**不在仓库里**）。
2. 插件 69 个：`workflow-aggregator / git / credentials-binding / junit / timestamper / allure` 及其依赖。
   所有 `.hpi` 从清华镜像离线下载后放进卷里，避免 updates.jenkins.io 在容器里只有 ~50KB/s。
3. Allure Commandline：全局工具里名为 `allure`，home=`/opt/allure`（2.29.0，手动安装，不走在线下载）。
4. 凭据：`lilishop-ui-username` / `lilishop-ui-password`，类型是 **Secret text**。
   为什么不是 Username with password：Declarative 的 `environment { X = credentials(id) }` 对
   用户名密码型会把 `X` 设成 `user:pass`（另有 `X_USR`/`X_PSW`），而 `TestConfig` 直接读
   `LILISHOP_USERNAME`，会拿到 `kk1024:111111` 导致接口登录失败。Secret text 才会原样注入。
5. Job：`ui-test-ai-for-java`，Pipeline script from SCM，Git URL `file:///src/UI-TEST-AI-FOR-JAVA`，
   script path `Jenkinsfile`。项目目录里因此初始化了一个本地 git 仓库（只有一个提交），
   Jenkinsfile 改动后要 `git commit` 才会被下一次构建取到。
6. 容器级环境变量 `LILISHOP_REDIS_HOST/PORT/PASSWORD`：cart 前置造数直连 Redis，Jenkinsfile 不传这些。

## 已知限制 / 风险

- **compose down/up 会重建网络**：`lilishop-net` 要是被测系统 compose 里没有声明，重建后 Jenkins 会掉线。
  应对：把网络名固定在 compose 文件里（`networks: { lilishop-net: { external: true } }`），
  或重建后重新 `docker network connect lilishop-net lilishop-all`。
- **docker.sock 等于宿主 root**：`DbFixture` 需要 `docker exec`，所以容器挂了宿主 socket，
  任何能在 Jenkins 里跑脚本的人都能控制宿主 Docker（起特权容器、读宿主文件）。这是本地单人环境的取舍；
  多用户环境应改成专用的造数服务（HTTP 网关）或把 DB 访问改成 JDBC 直连。
  本机 socket 路径是 `~/.docker/run/docker.sock`（Docker Desktop），不是 `/var/run/docker.sock`。
- **Allure 附件挂在用例的 Tear down 节点下**，不是红色步骤本身：`BrowserHooks` 在 `@After` 里取证，
  allure-cucumber7 把它们归到 fixture。点开失败用例 → 展开 Tear down 就能看到
  失败现场 / 失败截图 / 失败时 DOM / playwright trace。
- `@LLS-HOME-004` 的详情页标题断言偶发一次空字符串（首跑命中渲染时序），重跑通过；属于用例侧等待问题，未改代码。

## 卸载

```bash
docker rm -f jenkins
docker rmi jenkins-ui-test:1.0 jenkins-ui-test:1.1
docker volume rm jenkins_home jenkins_m2          # 会删掉 Jenkins 全部配置与 Maven 缓存
docker network disconnect lilishop-net lilishop-all
docker network rm lilishop-net
rm -rf jenkins/ ~/.jenkins-ui-test                # 构建上下文与本地密码文件
# 可选：撤销为了提供 Jenkinsfile 而建的本地 git 仓库
rm -rf /Users/lynnking/CodeBuddy/UI-TEST-AI-FOR-JAVA/.git
```
