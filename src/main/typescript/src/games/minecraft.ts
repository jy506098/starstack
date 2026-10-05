// games/minecraft.ts — 我的世界 (2D 体素沙盒). IIFE registers to window.Games['我的世界'].

(function (): void {
    const Games = (window as any).Games;

    const BLOCK_TYPES = [
        { name: '草', color: '#65a30d', top: '#84cc16' },
        { name: '土', color: '#92400e', top: '#a16207' },
        { name: '石', color: '#6b7280', top: '#9ca3af' },
        { name: '木', color: '#854d0e', top: '#a16207' },
        { name: '水', color: '#0369a1', top: '#0ea5e9' },
    ];

    Games['我的世界'] = function (canvas: HTMLCanvasElement) {
        const parent = canvas.parentElement!;
        const fit: { w: number; h: number } = Games.fitCanvas(canvas, 16 / 9);
        const ctx = canvas.getContext('2d')!;
        const w = fit.w, h = fit.h;
        const TILE = 32;
        const COLS = Math.floor(w / TILE) + 2;
        const ROWS = Math.floor(h / TILE) + 2;

        let world: Record<string, number> = {};
        let char = { x: 5, y: 0 };
        let currentType = 0;
        let message = '';
        let hud: any;
        const cam = { x: 0, y: 0 };

        function getBlock(c: number, r: number): number { return world[`${c},${r}`] ?? -1; }
        function setBlock(c: number, r: number, v: number): void {
            if (v < 0) delete world[`${c},${r}`];
            else world[`${c},${r}`] = v;
        }

        function init(): void {
            world = {};
            for (let c = 0; c < 30; c++) {
                const ground = 5 + Math.floor(Math.sin(c * 0.5) * 2 + Math.random() * 2);
                for (let r = 0; r < ROWS; r++) {
                    if (r > ground) world[`${c},${r}`] = r === ground + 1 ? 0 : (r < ground + 3 ? 1 : 2);
                    if (r === ground + 2 && Math.random() < 0.05) world[`${c},${r}`] = 3;
                }
            }
            char = { x: 5, y: 0 };
            currentType = 0;
            message = '左键挖方块 · 右键放置 · 1-5 选择方块';
            hud.setScore(0);
            hud.setStatus(message);
        }

        function score(): number { return Object.keys(world).length; }

        function render(): void {
            const sky = ctx.createLinearGradient(0, 0, 0, h);
            sky.addColorStop(0, '#0ea5e9'); sky.addColorStop(1, '#fef3c7');
            ctx.fillStyle = sky;
            ctx.fillRect(0, 0, w, h);

            ctx.fillStyle = '#fbbf24';
            ctx.beginPath(); ctx.arc(w - 60, 60, 28, 0, Math.PI * 2); ctx.fill();

            const startC = Math.floor(cam.x / TILE);
            const endC = startC + COLS;
            const startR = Math.floor(cam.y / TILE);
            const endR = startR + ROWS;

            for (let c = startC; c <= endC; c++) {
                for (let r = startR; r <= endR; r++) {
                    const b = getBlock(c, r);
                    if (b < 0) continue;
                    const x = c * TILE - cam.x, y = r * TILE - cam.y;
                    if (x > w || x < -TILE || y > h || y < -TILE) continue;
                    const bt = BLOCK_TYPES[b];
                    ctx.fillStyle = bt.color;
                    ctx.fillRect(x, y, TILE, TILE);
                    ctx.fillStyle = bt.top;
                    ctx.fillRect(x, y, TILE, 4);
                    ctx.strokeStyle = 'rgba(0,0,0,0.2)';
                    ctx.lineWidth = 1;
                    ctx.strokeRect(x + 0.5, y + 0.5, TILE - 1, TILE - 1);
                }
            }

            const chx = char.x * TILE - cam.x;
            const chy = char.y * TILE - cam.y;
            ctx.fillStyle = '#fbbf24';
            ctx.fillRect(chx + 8, chy, 16, 16);
            ctx.fillStyle = '#f97316';
            ctx.fillRect(chx + 4, chy + 16, 24, 24);
            ctx.fillStyle = '#fff';
            ctx.fillRect(chx + 11, chy + 5, 4, 4);
            ctx.fillRect(chx + 17, chy + 5, 4, 4);
            ctx.fillStyle = '#000';
            ctx.fillRect(chx + 12, chy + 6, 2, 2);
            ctx.fillRect(chx + 18, chy + 6, 2, 2);

            ctx.fillStyle = 'rgba(0,0,0,0.7)';
            ctx.fillRect(10, 10, 180, 36);
            ctx.fillStyle = '#fff';
            ctx.font = '14px monospace';
            ctx.textAlign = 'left';
            ctx.fillText(`方块: ${BLOCK_TYPES[currentType].name}`, 20, 28);
            ctx.fillText(`位置: (${char.x}, ${char.y})`, 20, 44);

            for (let i = 0; i < BLOCK_TYPES.length; i++) {
                const bx = 10 + i * 36, by = h - 50;
                ctx.fillStyle = i === currentType ? '#fbbf24' : 'rgba(255,255,255,0.1)';
                ctx.fillRect(bx, by, 32, 32);
                ctx.fillStyle = BLOCK_TYPES[i].color;
                ctx.fillRect(bx + 4, by + 4, 24, 24);
                ctx.fillStyle = '#000';
                ctx.font = 'bold 12px monospace';
                ctx.fillText(String(i + 1), bx + 2, by + 12);
            }

            ctx.fillStyle = '#fff';
            ctx.font = '12px monospace';
            ctx.textAlign = 'right';
            ctx.fillText(message, w - 10, 20);

            requestAnimationFrame(render);
        }

        function screenToWorld(mx: number, my: number): { c: number; r: number } {
            return { c: Math.floor((mx + cam.x) / TILE), r: Math.floor((my + cam.y) / TILE) };
        }

        canvas.addEventListener('click', (e: MouseEvent) => {
            const r = canvas.getBoundingClientRect();
            const mx = (e.clientX - r.left) * (canvas.width / r.width);
            const my = (e.clientY - r.top) * (canvas.height / r.height);
            const { c, r: row } = screenToWorld(mx, my);
            if (getBlock(c, row) >= 0) {
                setBlock(c, row, -1);
                message = `挖掉 (${c}, ${row})`;
                hud.setScore(score());
            }
        });
        canvas.addEventListener('contextmenu', (e: MouseEvent) => {
            e.preventDefault();
            const r = canvas.getBoundingClientRect();
            const mx = (e.clientX - r.left) * (canvas.width / r.width);
            const my = (e.clientY - r.top) * (canvas.height / r.height);
            const { c, r: row } = screenToWorld(mx, my);
            if (getBlock(c, row) < 0) {
                setBlock(c, row, currentType);
                message = `放置 ${BLOCK_TYPES[currentType].name} @ (${c}, ${row})`;
                hud.setScore(score());
            }
        });

        hud = Games.HUD(parent, { scoreLabel: '方块' });
        hud.on('restart', init);
        hud.on('pause', () => Games.pauseToggle.toggle());

        Games.keys.on('mc', (k: string, down: boolean) => {
            if (!down) return;
            if (k >= '1' && k <= '5') {
                currentType = parseInt(k) - 1;
                hud.setStatus(`选择: ${BLOCK_TYPES[currentType].name}`);
            }
            else if (k === 'w' || k === 'W' || k === 'ArrowUp') { char.y--; clampChar(); }
            else if (k === 's' || k === 'S' || k === 'ArrowDown') { char.y++; clampChar(); }
            else if (k === 'a' || k === 'A' || k === 'ArrowLeft') { char.x--; cam.x -= TILE; }
            else if (k === 'd' || k === 'D' || k === 'ArrowRight') { char.x++; cam.x += TILE; }
            cam.x = char.x * TILE - w / 2 + TILE / 2;
            cam.y = char.y * TILE - h / 2 + TILE / 2;
        });

        function clampChar(): void {
            char.x = Math.max(0, char.x);
            char.y = Math.max(0, char.y);
        }

        init();
        cam.x = char.x * TILE - w / 2 + TILE / 2;
        cam.y = char.y * TILE - h / 2 + TILE / 2;
        render();
    };
})();