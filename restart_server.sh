#!/bin/bash
# MapleStory v079 服务端重启脚本
# 用法: ./restart_server.sh
set -e

SERVER_DIR="/tmp/ms079-server"
PROJECT_DIR="F:/code/ms079"
LOG_DIR="$SERVER_DIR/logs"

echo "=== [1/4] 编译项目 ==="
cd "$PROJECT_DIR"
mvn clean package -DskipTests -q
echo "编译完成"

echo "=== [2/4] 停止旧服务端 ==="
cmd //c "taskkill /F /IM java.exe" 2>/dev/null
sleep 2
# 确认已停止
if tasklist 2>/dev/null | grep -qi java; then
    echo "警告: Java 进程仍在运行，再次尝试终止..."
    cmd //c "taskkill /F /IM java.exe" 2>/dev/null
    sleep 2
fi
echo "旧服务端已停止"

echo "=== [3/4] 部署新 jar ==="
cp "$PROJECT_DIR/target/ms079.jar" "$SERVER_DIR/ms079.jar"
echo "jar 已部署"

echo "=== [4/4] 启动服务端 ==="
cd "$SERVER_DIR"
rm -f "$LOG_DIR"/*.log 2>/dev/null
nohup java -server -Dwzpath=wz -cp "ms079.jar;lib/*" com.github.mrzhqiang.maplestory.MapleStoryApplication > /dev/null 2>&1 &
PID=$!
echo "服务端已启动 (PID: $PID)"

# 等待启动完成
echo -n "等待启动"
for i in {1..60}; do
    sleep 2
    echo -n "."
    if grep -q "游戏" "$LOG_DIR/application.log" 2>/dev/null; then
        echo ""
        echo "=== 启动成功！==="
        tail -3 "$LOG_DIR/application.log" | grep -i "启动\|成功\|游戏"
        exit 0
    fi
done
echo ""
echo "启动超时，请检查日志: tail -f $LOG_DIR/application.log"
