#!/usr/bin/env bash
# easy-bpm 产线部署脚本（在服务器上执行）
# 前提：与本脚本同目录下有 easy-bpm-server.jar 和 console-dist.zip
# 自动探测：后端运行方式（systemd / 裸 java 进程）、nginx 站点根目录
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_SRC="$DEPLOY_DIR/easy-bpm-server.jar"
DIST_SRC="$DEPLOY_DIR/console-dist.zip"

echo "=== 1. 探测后端运行方式 ==="
SYSTEMD_UNIT=""
if command -v systemctl >/dev/null; then
  SYSTEMD_UNIT=$(systemctl list-units --type=service --all --no-pager 2>/dev/null | grep -iE "easy-bpm|easybpm" | awk '{print $1}' | head -1 || true)
fi
JAVA_PID=$(pgrep -f "easy-bpm-server.jar" | head -1 || true)
echo "systemd unit: ${SYSTEMD_UNIT:-无}  java pid: ${JAVA_PID:-无}"

echo "=== 2. 定位当前 jar 与前端目录 ==="
if [ -n "$JAVA_PID" ]; then
  OLD_JAR=$(tr '\0' ' ' < /proc/$JAVA_PID/cmdline | grep -oE '[^ ]*easy-bpm-server\.jar' | head -1)
  echo "运行中 jar: $OLD_JAR"
else
  OLD_JAR=""
  echo "后端未在运行，将只替换文件"
fi

NGINX_ROOT=$(grep -rE "^\s*root\s+" /etc/nginx/sites-enabled /etc/nginx/conf.d /etc/nginx/nginx.conf 2>/dev/null | grep -iE "bpm|dist" | awk '{print $2}' | tr -d ';' | head -1 || true)
if [ -z "$NGINX_ROOT" ]; then
  NGINX_ROOT=$(find /opt /srv /usr/share/nginx /var/www -maxdepth 3 -name "index.html" -path "*bpm*" 2>/dev/null | xargs -r -n1 dirname | head -1)
fi
echo "前端站点根目录: ${NGINX_ROOT:-未找到（请手动指定）}"

echo "=== 3. 备份 ==="
TS=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR=/opt/easy-bpm-backup/$TS
mkdir -p "$BACKUP_DIR"
[ -n "$OLD_JAR" ] && [ -f "$OLD_JAR" ] && cp "$OLD_JAR" "$BACKUP_DIR/" && echo "已备份 jar -> $BACKUP_DIR"
[ -n "$NGINX_ROOT" ] && tar czf "$BACKUP_DIR/web_$(basename $NGINX_ROOT).tgz" -C "$(dirname $NGINX_ROOT)" "$(basename $NGINX_ROOT)" && echo "已备份前端目录 -> $BACKUP_DIR"

echo "=== 4. 停后端 ==="
if [ -n "$SYSTEMD_UNIT" ]; then
  systemctl stop "$SYSTEMD_UNIT"
  echo "systemctl stop $SYSTEMD_UNIT"
elif [ -n "$JAVA_PID" ]; then
  kill "$JAVA_PID"
  for i in $(seq 1 15); do kill -0 "$JAVA_PID" 2>/dev/null || break; sleep 1; done
  kill -0 "$JAVA_PID" 2>/dev/null && kill -9 "$JAVA_PID"
  echo "已停止 java 进程 $JAVA_PID"
fi

echo "=== 5. 替换 jar 与前端 ==="
if [ -n "$OLD_JAR" ]; then
  cp "$JAR_SRC" "$OLD_JAR"
  echo "jar 已更新: $OLD_JAR"
fi
if [ -n "$NGINX_ROOT" ]; then
  rm -rf "$NGINX_ROOT"/*
  unzip -q "$DIST_SRC" -d "$NGINX_ROOT"
  echo "前端已更新: $NGINX_ROOT ($(ls "$NGINX_ROOT" | wc -l) 个条目)"
fi

echo "=== 6. 执行数据库迁移（幂等，若有未应用脚本） ==="
# 本次改动无新增迁移，占位提醒即可

echo "=== 7. 启动后端 ==="
if [ -n "$SYSTEMD_UNIT" ]; then
  systemctl start "$SYSTEMD_UNIT"
  echo "systemctl start $SYSTEMD_UNIT"
else
  echo "!! 未检测到 systemd 服务，请按原有方式启动后端（如：SPRING_PROFILES_ACTIVE=prod SERVER_PORT=8083 nohup java -jar ... &）"
fi

echo "=== 8. 健康检查 ==="
for i in $(seq 1 30); do
  code=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8083/actuator/health 2>/dev/null || true)
  [ "$code" = "200" ] && { echo "后端健康 200（第 ${i} 次探测）"; break; }
  sleep 2
done
curl -s -o /dev/null -w "登录接口 POST /auth/login -> %{http_code}（400=参数校验，说明路由通了）\n" -X POST http://127.0.0.1:8083/auth/login -H 'Content-Type: application/json' -d '{}'
echo "=== 部署完成 ==="
