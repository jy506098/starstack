// games/fruitninja.ts — 水果忍者. IIFE registers to window.Games['水果忍者'].

(function (): void {
    const Games = (window as any).Games;

    const FRUITS = ['🍎', '🍊', '🍋', '🍉', '🍇', '🍓', '🍑', '🍌', '🥝', '🍍'];
    const COLORS = ['#ef4444', '#f97316', '#fbbf24', '#22c55e', '#a855f7', '#ec4899', '#fb7185', '#facc15', '#84cc16', '#f59e0b'];

    interface Fruit { x: number; y: number; vx: number; vy: number; r: number; color: string; emoji: string; rot: number; rotSpd: number; sliced: boolean; bomb?: boolean; }
    interface Slice { x: number; y: number; vx: number; vy: number; color: string; life: number; }

    Games['水果忍者'] = function (canvas: HTMLCanvasElement) {
        const parent = canvas.parentElement!;
        const fit: { w: number; h: number } = Games.fitCanvas(canvas, 9 / 16);
        const ctx = canvas.getContext('2d')!;
        const w = fit.w, h = fit.h;

        let fruits: Fruit[] = [], slices: Slice[] = [], score = 0, lives = 3, over = false, spawnTimer = 0;
        let hud: any;

        function init(): void {
            fruits = []; slices = []; score = 0; lives = 3; over = false; spawnTimer = 0;
            hud.setScore(0);
            hud.setStatus(`生命: ${'❤'.repeat(lives)}`);
        }

        function spawn(): void {
            const x = 80 + Math.random() * (w - 160);
            fruits.push({
                x, y: h + 30,
                vx: (w / 2 - x) * 0.012,
                vy: -12 - Math.random() * 3,
                r: 28, color: COLORS[Math.floor(Math.random() * COLORS.length)],
                emoji: FRUITS[Math.floor(Math.random() * FRUITS.length)],
                rot: 0, rotSpd: (Math.random() - 0.5) * 0.15, sliced: false,
            });
            if (Math.random() < 0.15) {
                fruits.push({
                    x: 80 + Math.random() * (w - 160), y: h + 30,
                    vx: (w / 2 - x) * 0.012, vy: -12 - Math.random() * 3,
                    r: 26, color: '#000', emoji: '💣', rot: 0, rotSpd: 0, sliced: false, bomb: true,
                });
            }
        }

        function sliceAt(x: number, y: number): void {
            for (const f of fruits) {
                if (f.sliced) continue;
                const dx = f.x - x, dy = f.y - y;
                if (Math.sqrt(dx * dx + dy * dy) < f.r) {
                    f.sliced = true;
                    if (f.bomb) { over = true; hud.setStatus('💥 切到炸弹！'); return; }
                    score += 10;
                    hud.setScore(score);
                    slices.push({ x: f.x, y: f.y, vx: f.vx - 2, vy: f.vy - 2, color: f.color, life: 1 });
                    slices.push({ x: f.x, y: f.y, vx: f.vx + 2, vy: f.vy - 2, color: f.color, life: 1 });
                }
            }
        }

        function update(dt: number): void {
            if (over) return;
            spawnTimer += dt;
            const interval = Math.max(450, 1200 - score * 5);
            if (spawnTimer > interval) { spawnTimer = 0; spawn(); }
            for (const f of fruits) {
                f.x += f.vx; f.y += f.vy; f.vy += 0.4; f.rot += f.rotSpd;
                if (f.y > h + 50 && !f.sliced && !f.bomb) {
                    f.sliced = true;
                    lives--;
                    if (lives <= 0) { over = true; hud.setStatus('💀 游戏结束'); }
                    else hud.setStatus(`生命: ${'❤'.repeat(lives)}`);
                }
            }
            fruits = fruits.filter(f => f.y < h + 100);
            for (const s of slices) { s.x += s.vx; s.y += s.vy; s.vy += 0.4; s.life -= 0.02; }
            slices = slices.filter(s => s.life > 0);
        }

        function render(): void {
            const g = ctx.createLinearGradient(0, 0, 0, h);
            g.addColorStop(0, '#fef3c7'); g.addColorStop(1, '#fed7aa');
            ctx.fillStyle = g;
            ctx.fillRect(0, 0, w, h);
            ctx.strokeStyle = 'rgba(139, 69, 19, 0.15)';
            for (let i = 0; i < h; i += 30) {
                ctx.beginPath();
                ctx.moveTo(0, i); ctx.lineTo(w, i + (Math.sin(i) * 10));
                ctx.stroke();
            }
            ctx.font = `56px serif`;
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            for (const f of fruits) {
                ctx.save();
                ctx.translate(f.x, f.y); ctx.rotate(f.rot);
                ctx.fillStyle = 'rgba(0,0,0,0.2)';
                ctx.beginPath();
                ctx.ellipse(0, f.r * 0.7, f.r * 0.7, 6, 0, 0, Math.PI * 2);
                ctx.fill();
                ctx.fillText(f.emoji, 0, 0);
                ctx.restore();
            }
            for (const s of slices) {
                ctx.globalAlpha = s.life;
                ctx.fillStyle = s.color;
                ctx.beginPath();
                ctx.arc(s.x, s.y, 12, 0, Math.PI * 2);
                ctx.fill();
                ctx.fillStyle = '#fff';
                ctx.font = 'bold 16px serif';
                ctx.fillText('汁', s.x, s.y);
            }
            ctx.globalAlpha = 1;
            requestAnimationFrame(render);
        }

        let lastX: number | null = null, lastY: number | null = null;
        function drawTrail(x: number, y: number): void {
            const prevX = lastX, prevY = lastY;
            lastX = x; lastY = y;
            if (prevX === null || prevY === null) return;
            ctx.save();
            ctx.globalCompositeOperation = 'source-over';
            ctx.strokeStyle = 'rgba(255, 255, 255, 0.8)';
            ctx.lineWidth = 6; ctx.lineCap = 'round';
            ctx.beginPath();
            ctx.moveTo(prevX, prevY); ctx.lineTo(x, y);
            ctx.stroke();
            ctx.strokeStyle = 'rgba(255, 215, 0, 0.9)';
            ctx.lineWidth = 3; ctx.stroke();
            ctx.restore();
            setTimeout(() => { lastX = null; lastY = null; }, 80);
        }

        function onMove(x: number, y: number): void { drawTrail(x, y); sliceAt(x, y); }

        canvas.addEventListener('mousemove', (e: MouseEvent) => {
            const r = canvas.getBoundingClientRect();
            onMove((e.clientX - r.left) * (canvas.width / r.width), (e.clientY - r.top) * (canvas.height / r.height));
        });
        canvas.addEventListener('touchmove', (e: TouchEvent) => {
            const r = canvas.getBoundingClientRect();
            const t = e.touches[0];
            onMove((t.clientX - r.left) * (canvas.width / r.width), (t.clientY - r.top) * (canvas.height / r.height));
            e.preventDefault();
        }, { passive: false });

        hud = Games.HUD(parent);
        hud.on('restart', init);
        hud.on('pause', () => Games.pauseToggle.toggle());

        let last = performance.now();
        function loop(t: number): void {
            const dt = t - last; last = t;
            if (!Games.pauseToggle.get()) update(dt);
            requestAnimationFrame(loop);
        }

        init();
        render();
        requestAnimationFrame(loop);
    };
})();