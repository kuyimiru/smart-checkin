#!/bin/bash
set -e

echo "============================================"
echo "  智能打卡小程序 - 后端服务启动"
echo "============================================"
echo ""

cd "$(dirname "$0")/backend"

# 检查 Java
if ! command -v java &>/dev/null; then
    echo "[错误] 未找到 Java 环境，请先安装 JDK 17+"
    exit 1
fi

# 检查 Maven
if ! command -v mvn &>/dev/null; then
    echo "[错误] 未找到 Maven，请先安装 Maven 3.6+"
    exit 1
fi

# 检查 MySQL
echo "[检查] 正在验证 MySQL 连接..."
if ! mysql -uroot -proot -e "SELECT 1" smart_checkin &>/dev/null; then
    echo "[警告] MySQL 连接失败，请确保："
    echo "  1. MySQL 服务已启动"
    echo "  2. 已执行 smart_checkin.sql 建库"
    echo "  3. application.yml 中用户名密码正确"
    read -p "是否仍然尝试启动？(y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then exit 1; fi
fi

echo ""
echo "[启动] 正在编译并启动后端服务..."
echo "       访问地址: http://localhost:8080"
echo "       按 Ctrl+C 停止服务"
echo "============================================"
echo ""

mvn spring-boot:run
