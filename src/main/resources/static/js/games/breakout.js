"use strict";
// games/breakout.ts — 打砖块. IIFE registers to window.Games['打砖块'].
(function () {
    const w = window;
    w.Games = w.Games || {};
    w.Games['打砖块'] = (canvas, status) => {
        const ctx = canvas.getContext('2d');
        if (!ctx)
            return;
        canvas.width = 480;
        canvas.height = 320;
        let paddleX = 200, ballX = 240, ballY = 160, dx = 2, dy = -2;
        const bricks = [];
        for (let row = 0; row < 5; row++) {
            for (let col = 0; col < 8; col++) {
                bricks.push({ x: 60 + col * 50, y: 30 + row * 20, alive: true });
            }
        }
        let raf = 0;
        function loop() {
            if (!ctx)
                return;
            ctx.fillStyle = '#0a0a14';
            ctx.fillRect(0, 0, 480, 320);
            // Paddle
            ctx.fillStyle = '#06d6a0';
            ctx.fillRect(paddleX, 300, 80, 8);
            // Ball
            ballX += dx;
            ballY += dy;
            if (ballX < 0 || ballX > 480)
                dx = -dx;
            if (ballY < 0)
                dy = -dy;
            if (ballY > 320) {
                status.textContent = '游戏结束 — 按 R 重启';
                cancelAnimationFrame(raf);
                return;
            }
            if (ballY > 292 && ballX > paddleX && ballX < paddleX + 80)
                dy = -dy;
            ctx.fillStyle = '#ff4d6d';
            ctx.beginPath();
            ctx.arc(ballX, ballY, 6, 0, Math.PI * 2);
            ctx.fill();
            // Bricks
            bricks.forEach(b => {
                if (!b.alive)
                    return;
                ctx.fillStyle = '#3a86ff';
                ctx.fillRect(b.x, b.y, 46, 14);
                if (ballX > b.x && ballX < b.x + 46 && ballY > b.y && ballY < b.y + 14) {
                    b.alive = false;
                    dy = -dy;
                }
            });
            const alive = bricks.filter(b => b.alive).length;
            status.textContent = `剩余砖块: ${alive}`;
            if (alive === 0) {
                status.textContent = '胜利！按 R 重启';
                cancelAnimationFrame(raf);
                return;
            }
            raf = requestAnimationFrame(loop);
        }
        loop();
        canvas.tabIndex = 0;
        canvas.focus();
        canvas.addEventListener('keydown', e => {
            if (e.key === 'ArrowLeft')
                paddleX = Math.max(0, paddleX - 20);
            else if (e.key === 'ArrowRight')
                paddleX = Math.min(400, paddleX + 20);
            else if (e.key === 'r' || e.key === 'R')
                w.Games['打砖块'](canvas, status);
        });
    };
})();
//# sourceMappingURL=breakout.js.map