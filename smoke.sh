#!/usr/bin/env bash
# End-to-end smoke test for StarStack.
# Requires: bash, curl, sqlite3 (optional, for DB inspection), python3 (for URL encoding).
#
# Run while a StarStack server is up at http://localhost:5000.

set -u
BASE=http://localhost:80
COOKIES=/tmp/starstack_smoke.cookies
PASS=0
FAIL=0
LOG=/tmp/starstack_smoke.log

ok()  { echo "✓ $1"; PASS=$((PASS+1)); }
fail(){ echo "✗ $1"; FAIL=$((FAIL+1)); }

urlenc() {
    # URL-encode a UTF-8 string using Python
    python3 -c "import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1]))" "$1"
}

check() {
    local desc="$1" code="$2" want="$3"
    if [ "$code" = "$want" ]; then ok "$desc → $code"; else fail "$desc → $code (want $want)"; fi
}

rm -f "$COOKIES" "$LOG"

echo "=== 1. Public endpoints (anon) ==="
check "/login"     "$(curl -s -o /dev/null -w '%{http_code}' $BASE/login)"     200
check "/register"  "$(curl -s -o /dev/null -w '%{http_code}' $BASE/register)"  200
check "/ (anon)"   "$(curl -s -o /dev/null -w '%{http_code}' $BASE/)"        200
check "/api/vip_status (anon)" "$(curl -s -o /dev/null -w '%{http_code}' $BASE/api/vip_status)" 200
check "/css/style.css"  "$(curl -s -o /dev/null -w '%{http_code}' $BASE/css/style.css)" 200
check "/js/pages/main.js" "$(curl -s -o /dev/null -w '%{http_code}' $BASE/js/pages/main.js)" 200

echo "=== 2. Register + login ==="
USER=smoke_$(date +%s)
curl -s -c "$COOKIES" -X POST -d "username=$USER&password=pass1234" -o /dev/null "$BASE/register"
curl -s -c "$COOKIES" -b "$COOKIES" -X POST -d "username=$USER&password=pass1234" -o /dev/null "$BASE/login"

echo "=== 3. Authenticated GETs ==="
for p in / /message_board /store /inventory /tasks /settings /vip /recharge /api/vip_status /api/mc_server_info; do
    check "$p" "$(curl -s -b $COOKIES -o /dev/null -w '%{http_code}' $BASE$p)" 200
done
echo "  MC info: $(curl -s -b $COOKIES $BASE/api/mc_server_info)"

echo "=== 4. Recharge + confirm ==="
OID=$(curl -s -b "$COOKIES" -X POST -d "pkg=RECHARGE-2000" -o /dev/null -w '%{redirect_url}' "$BASE/buy_recharge" | grep -oE '[^/]+$')
check "recharge_pay_confirm/$OID" "$(curl -s -b $COOKIES -o /dev/null -w '%{http_code}' $BASE/recharge_pay_confirm/$OID)" 302

echo "=== 5. Buy padlock (游戏手柄) + all 10 dispatched games ==="
PAD=$(urlenc "游戏手柄")
curl -s -b "$COOKIES" -X POST --data "item=$PAD" -o /dev/null -w "padlock %{http_code}\n" "$BASE/buy"
for g in "打砖块" "2048" "飞扬的小鸟" "水果忍者" "扫雷" "我的世界" "俄罗斯方块" "雷电战机" "成语接龙" "抛硬币小游戏"; do
    U=$(urlenc "$g")
    curl -s -b "$COOKIES" -X POST --data "game=$U" -o /dev/null -w "$g buy %{http_code}\n" "$BASE/buy_game"
done

echo "=== 6. /play_game returns 200 with correct scripts ==="
for g in "打砖块" "2048" "飞扬的小鸟" "水果忍者" "扫雷" "我的世界" "俄罗斯方块" "雷电战机" "成语接龙" "抛硬币小游戏"; do
    U=$(urlenc "$g")
    HTML=/tmp/play_${g}.html
    CODE=$(curl -s -b "$COOKIES" -o "$HTML" -w '%{http_code}' "$BASE/play_game/$U")
    SCRIPTS=$(grep -oE 'js/games/[a-z0-9_]+\.js' "$HTML" | sort -u | tr '\n' ',')
    check "play_game/$g → $CODE ($SCRIPTS)" "$CODE" 200
done

echo "=== 7. Buy + play snake (free WebSocket) ==="
SNK=$(urlenc "贪吃蛇大作战")
curl -s -b "$COOKIES" -X POST --data "game=$SNK" -o /dev/null "$BASE/buy_game"
SNAKE=/tmp/snake.html
check "play_game/贪吃蛇大作战" "$(curl -s -b $COOKIES -o $SNAKE -w '%{http_code}' $BASE/play_game/$SNK)" 200
grep -q "snake.js" "$SNAKE" && ok "snake.html loads /js/pages/snake.js" || fail "snake.html missing snake.js"

echo "=== 8. WebSocket handshake ==="
WS_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
    -H 'Connection: Upgrade' -H 'Upgrade: websocket' \
    -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' \
    -H 'Sec-WebSocket-Version: 13' \
    --max-time 2 "$BASE/snake_ws")
check "/snake_ws handshake" "$WS_CODE" 101

echo "=== 9. Buy a tutorial + use item (编程秘籍 → /content/编程秘籍) ==="
TUT=$(urlenc "编程秘籍")
curl -s -b "$COOKIES" -X POST --data "item=$TUT" -o /dev/null -w "buy tutorial %{http_code}\n" "$BASE/buy"
T=$(urlenc "编程秘籍")
check "/use_item → /content/编程秘籍" "$(curl -s -b $COOKIES -X POST --data "item=$T" -o /dev/null -w '%{http_code}' $BASE/use_item)" 302

echo "=== Summary ==="
echo "  PASS: $PASS"
echo "  FAIL: $FAIL"
[ "$FAIL" = "0" ] && exit 0 || exit 1