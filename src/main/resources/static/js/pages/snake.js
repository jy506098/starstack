"use strict";
// pages/snake.ts — connect to /snake_ws, render the game.
const canvas = document.getElementById('game-canvas');
const statusEl = document.getElementById('game-status');
const ctx2d = canvas.getContext('2d');
const CELL = 16;
let ws = null;
let myName = '';
let room = 'default';
let state = null;
let lastInput = 0;
function draw() {
    if (!ctx2d || !state)
        return;
    const w = state.w * CELL;
    const h = state.h * CELL;
    canvas.width = w;
    canvas.height = h;
    ctx2d.fillStyle = '#0a0a14';
    ctx2d.fillRect(0, 0, canvas.width, canvas.height);
    ctx2d.fillStyle = '#ff4d6d';
    state.foods.forEach(f => {
        ctx2d.beginPath();
        ctx2d.arc(f[0] * CELL + CELL / 2, f[1] * CELL + CELL / 2, CELL / 3, 0, Math.PI * 2);
        ctx2d.fill();
    });
    state.players.forEach(p => {
        ctx2d.fillStyle = p.alive ? p.color : '#555';
        p.snake.forEach((s, i) => {
            if (i === 0) {
                ctx2d.fillRect(s[0] * CELL + 1, s[1] * CELL + 1, CELL - 2, CELL - 2);
            }
            else {
                ctx2d.globalAlpha = 0.85;
                ctx2d.fillRect(s[0] * CELL + 2, s[1] * CELL + 2, CELL - 4, CELL - 4);
                ctx2d.globalAlpha = 1;
            }
        });
    });
}
function setStatus(text) { statusEl.textContent = text; }
function input(dx, dy) {
    if (!ws || ws.readyState !== WebSocket.OPEN)
        return;
    const now = Date.now();
    if (now - lastInput < 80)
        return;
    lastInput = now;
    ws.send(JSON.stringify({ type: 'input', dir: [dx, dy] }));
}
function connect() {
    const proto = location.protocol === 'https:' ? 'wss' : 'ws';
    ws = new WebSocket(`${proto}://${location.host}/snake_ws`);
    ws.onopen = () => {
        ws?.send(JSON.stringify({ type: 'join', room, name: myName }));
        setStatus(`已连接 · 房间 ${room}`);
    };
    ws.onmessage = (e) => {
        try {
            const m = JSON.parse(e.data);
            if (m.type === 'state') {
                state = m;
                draw();
            }
            else if (m.type === 'welcome') {
                setStatus(`已加入 · 房间 ${m.room}`);
            }
            else if (m.type === 'gameover') {
                setStatus(`游戏结束 · 胜者 ${m.winner}`);
            }
            else if (m.type === 'error') {
                setStatus(`错误: ${m.msg}`);
            }
        }
        catch { /* */ }
    };
    ws.onclose = () => setStatus('连接已断开');
    ws.onerror = () => setStatus('WebSocket 错误');
}
document.addEventListener('DOMContentLoaded', () => {
    myName = prompt('请输入玩家名', '玩家' + Math.floor(Math.random() * 100)) || '玩家';
    room = (new URLSearchParams(location.search).get('room') || 'default');
    canvas.width = 40 * CELL;
    canvas.height = 30 * CELL;
    if (ctx2d) {
        ctx2d.fillStyle = '#0a0a14';
        ctx2d.fillRect(0, 0, canvas.width, canvas.height);
    }
    setStatus('正在连接...');
    connect();
    window.addEventListener('keydown', (e) => {
        const k = e.key;
        if (k === 'ArrowUp' || k === 'w') {
            e.preventDefault();
            input(0, -1);
        }
        else if (k === 'ArrowDown' || k === 's') {
            e.preventDefault();
            input(0, 1);
        }
        else if (k === 'ArrowLeft' || k === 'a') {
            e.preventDefault();
            input(-1, 0);
        }
        else if (k === 'ArrowRight' || k === 'd') {
            e.preventDefault();
            input(1, 0);
        }
    });
});
//# sourceMappingURL=snake.js.map