#!/usr/bin/env bash
# Jenkins 容器的入口：先在 127.0.0.1:80 起一个 TCP 转发，再启动 Jenkins。
#
# 为什么需要它：被测前端（lilishop 买家端）的 /config.js 把 API 地址写死成
# http://127.0.0.1:80/api。浏览器在 Jenkins 容器里跑时，127.0.0.1 是容器自己，
# 不是被测系统，页面会白屏并且 /api 请求全部 ERR_CONNECTION_REFUSED。
# 这个转发把 127.0.0.1:80 指到被测容器，使「页面源」和「API 源」都是 127.0.0.1，
# 顺便让注入的 cookie（domain=127.0.0.1）与 API 同源。
#
# 不需要时可以删掉本文件、Dockerfile 里的 socat 和 ENTRYPOINT，改用 BASE_URL=http://<被测容器名>。
set -euo pipefail

SUT_HOST="${SUT_HOST:-lilishop-all}"
SUT_PORT="${SUT_PORT:-80}"

socat "TCP-LISTEN:80,fork,reuseaddr,bind=127.0.0.1" "TCP:${SUT_HOST}:${SUT_PORT}" &

exec java -Xms256m -Xmx1536m \
  -Duser.timezone=Asia/Shanghai \
  -Dhudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT=true \
  -jar /opt/jenkins/jenkins.war \
  --httpPort=8080 --httpListenAddress=0.0.0.0
