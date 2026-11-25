# Jenkins 单容器：Playwright 1.49.0 官方镜像 + JDK + Maven + Allure CLI
#
# 为什么用 mcr.microsoft.com/playwright:v1.49.0-jammy 而不是 jenkins/jenkins：
#   - tag 与项目 pom 的 Playwright 1.49.0 对齐，/ms-playwright 下的 chromium 版本与 Java 客户端匹配
#   - 浏览器路径、系统依赖（字体/中文字体/alsa 等）都是官方配好的，不需要手装 Chrome
#   - Linux arm64 没有 Google Chrome 官方包，所以 BROWSER_CHANNEL 只能取 none
#
# 构建上下文（本目录）需要：Dockerfile、jenkins.war、allure-2.29.0.zip、m2-settings.xml
FROM mcr.microsoft.com/playwright:v1.49.0-jammy

USER root

ENV DEBIAN_FRONTEND=noninteractive \
    LANG=C.UTF-8 \
    TZ=Asia/Shanghai \
    JENKINS_HOME=/var/jenkins_home

# Jenkins 2.580.x 的 class 文件版本是 65，运行时必须 JDK 21（JDK 17 会在启动时报 UnsupportedClassVersionError）。
# 项目本身仍然按 pom 的 release=17 编译，不受影响。
# apt 走清华 ubuntu-ports 镜像：官方 ports.ubuntu.com 在国内经常几十 KB/s，构建会卡在 apt 阶段。
RUN sed -i 's#http://ports.ubuntu.com/ubuntu-ports#https://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports#g; s#http://archive.ubuntu.com/ubuntu#https://mirrors.tuna.tsinghua.edu.cn/ubuntu#g' \
      /etc/apt/sources.list /etc/apt/sources.list.d/* 2>/dev/null || true
RUN apt-get -o Acquire::Retries=3 update \
 && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
      openjdk-21-jdk maven unzip ca-certificates tzdata socat \
 && rm -rf /var/lib/apt/lists/*

# JAVA_HOME：用 readlink 解出真实路径，再断言 javac 存在。
# arm64 上实际是 /usr/lib/jvm/java-21-openjdk-arm64；如果基础镜像架构变了，这里会构建失败而不是运行期才炸。
RUN readlink -f "$(readlink -f "$(command -v java)")"
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
RUN test -x "$JAVA_HOME/bin/javac" && java -version 2>&1 | head -3 && mvn -v | head -3

# Allure CLI 2.29.x（与 pom 的 allure.version=2.29.1 同一条线）。
# 装到 /opt/allure，由 Allure 插件的「全局工具配置 → Allure Commandline → 手动安装目录」指向它，
# 避免插件运行期从 GitHub 在线下载（国内网络经常超时）。
COPY allure-2.29.0.zip /tmp/
RUN unzip -q /tmp/allure-2.29.0.zip -d /opt \
 && mv /opt/allure-2.29.0 /opt/allure \
 && rm -f /tmp/allure-2.29.0.zip \
 && /opt/allure/bin/allure --version

COPY jenkins.war /opt/jenkins/jenkins.war

# Maven 走阿里云镜像；只挂载 /root/.m2/repository 时这份 settings 不会被卷遮住
COPY m2-settings.xml /root/.m2/settings.xml

# 宿主目录以只读方式挂到 /src，属主是 uid 501；容器里以 root 跑 git 会把仓库判成
# "dubious ownership" 而拒绝 clone，这里显式放行这一个固定路径。
RUN git config --system --add safe.directory /src/caseforge-ui

# Docker CLI（静态二进制）：DbFixture 用 `docker exec lilishop-all mysql ...` 造数，
# 因此需要 CLI + 运行期挂载宿主 docker.sock。CLI 版本 28.5.1 与 daemon 1.56 API 兼容。
COPY docker-cli /usr/local/bin/docker
RUN chmod +x /usr/local/bin/docker && docker --version

COPY entrypoint.sh /usr/local/bin/jenkins-entrypoint.sh
RUN chmod +x /usr/local/bin/jenkins-entrypoint.sh

EXPOSE 8080

# 入口脚本：先在 127.0.0.1:80 转发到被测站点，再启动 Jenkins（原因见 entrypoint.sh）
ENTRYPOINT ["/usr/local/bin/jenkins-entrypoint.sh"]
