#!/usr/bin/env bash
set -e
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORT="${1:-8088}"
if ! command -v python3 >/dev/null 2>&1; then
  echo "需要 Python 3.9 或以上版本。安装后重新运行。"
  exit 1
fi
if [ ! -f "$DIR/dist/index.html" ]; then
  echo "PC 页面尚未构建，请运行 pc/build.sh。"
  exit 1
fi
while ! python3 - "$PORT" <<'CHECK'
import socket, sys
with socket.socket() as s:
    try: s.bind(('0.0.0.0', int(sys.argv[1])))
    except OSError: sys.exit(1)
CHECK
do PORT=$((PORT + 1)); done
echo "手机连接地址：这台电脑的局域网 IP:$PORT；密钥见 PC 系统设置。"
if command -v open >/dev/null 2>&1; then
  (sleep 1 && open "http://localhost:$PORT/console/") &
elif command -v xdg-open >/dev/null 2>&1; then
  (sleep 1 && xdg-open "http://localhost:$PORT/console/") &
fi
exec python3 "$DIR/server.py" --port "$PORT" --auto-port
