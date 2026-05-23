#!/bin/bash
# MapleStory v079 服务端 —— 编译 & 重启脚本
# 用法: ./restart_server.sh        (命令行模式)
#       ./restart_server.sh gui    (GUI 模式)
set -e

cd "$(dirname "$0")"
HOME="$(pwd)"
PID_FILE="$HOME/ms079.pid"
LOG_DIR="$HOME/logs"

MAIN_CLASS="com.github.mrzhqiang.maplestory.MapleStoryApplication"
MODE="命令行"
if [ "$1" = "gui" ]; then
    MAIN_CLASS="gui.GUIApplication"
    MODE="GUI"
fi

echo "========================================"
echo "  MapleStory v079 服务端重启脚本"
echo "  模式: $MODE"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "========================================"

# ============================================================
# [1/4] 停止旧进程
# ============================================================
echo ""
echo ">>> [1/4] 停止旧进程..."

if [ -f "$PID_FILE" ]; then
    OLD_PID=$(cat "$PID_FILE")
    if [ -n "$OLD_PID" ]; then
        echo "  发现 PID 文件: $OLD_PID"
        taskkill //F //PID "$OLD_PID" 2>/dev/null || true
    fi
    rm -f "$PID_FILE"
fi

if command -v jps &>/dev/null; then
    JPS_RESULT=$(jps -l 2>/dev/null || true)
    echo "$JPS_RESULT" | while read -r pid name; do
        case "$name" in
            *MapleStoryApplication*|*GUIApplication*)
                echo "  发现残留进程: $pid ($name)，正在终止..."
                taskkill //F //PID "$pid" 2>/dev/null || true
                ;;
        esac
    done
else
    echo "  警告: jps 不可用，跳过进程扫描"
fi

taskkill //FI "WINDOWTITLE eq MapleStory_079" //F 2>/dev/null || true

sleep 1
echo "  旧进程已清理"

# ============================================================
# [2/4] 编译打包（含 EBean 增强）
# ============================================================
echo ""
echo ">>> [2/4] 编译打包..."
mvn clean package -DskipTests
echo "  打包完成"

# ============================================================
# [3/4] 准备运行环境
# ============================================================
echo ""
echo ">>> [3/4] 准备运行环境..."
mkdir -p "$LOG_DIR"

# 解压分发包到 target/runtime，获取完整 lib/（含 Netty 等所有依赖）
RUNTIME_DIR="$HOME/target/runtime"
rm -rf "$RUNTIME_DIR"
mkdir -p "$RUNTIME_DIR"
DIST_ZIP="$HOME/target/ms079-1.0-SNAPSHOT-dist.zip"
echo "  解压分发包..."
unzip -qo "$DIST_ZIP" -d "$RUNTIME_DIR"

# 复制 wz 资源目录（分发包里已有，但确保最新）
if [ -d "$HOME/wz" ]; then
    cp -r "$HOME/wz" "$RUNTIME_DIR/wz" 2>/dev/null || true
fi

echo "  运行环境就绪: $RUNTIME_DIR"

# ============================================================
# [4/4] 启动服务端
# ============================================================
echo ""
echo ">>> [4/4] 启动服务端 ($MODE 模式)..."

JAVA_OPTS="-server -Dwzpath=wz"
nohup java $JAVA_OPTS -cp "target/runtime/ms079.jar;target/runtime/lib/*" $MAIN_CLASS \
    > "$LOG_DIR/server.log" 2>&1 &
disown

NEW_PID=$!
echo "$NEW_PID" > "$PID_FILE"
echo "  进程已启动 (PID: $NEW_PID)"
echo "  日志: $LOG_DIR/server.log"

# ============================================================
# 等待确认
# ============================================================
echo ""
echo -n "  等待启动"
for i in $(seq 1 30); do
    sleep 2
    echo -n "."
    if ! kill -0 "$NEW_PID" 2>/dev/null; then
        echo ""
        echo "  ✗ 进程异常退出！查看日志:"
        tail -30 "$LOG_DIR/server.log"
        rm -f "$PID_FILE"
        exit 1
    fi
done

echo ""
echo ""
echo "========================================"
echo "  服务端启动完成 (PID: $NEW_PID)"
echo "  查看日志: tail -f $LOG_DIR/server.log"
echo "========================================"
