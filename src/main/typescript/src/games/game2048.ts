// games/game2048.ts — 2048. IIFE registers to window.Games['2048'].

(function (): void {
    const Games = (window as any).Games;

    Games['2048'] = function (canvas: HTMLCanvasElement) {
        const parent = canvas.parentElement!;
        const SIZE = 4;
        const fit: { w: number; h: number } = Games.fitCanvas(canvas, 1);
        const w = fit.w, h = fit.h;
        const TILE = Math.floor(Math.min(w, h) / (SIZE + 0.5));
        const offX = (w - TILE * SIZE) / 2;
        const offY = (h - TILE * SIZE) / 2;
        const ctx = canvas.getContext('2d')!;

        const COLORS: Record<number, string> = {
            0: '#1a1a2e', 2: '#fef3c7', 4: '#fde68a', 8: '#fbbf24',
            16: '#f59e0b', 32: '#ef4444', 64: '#dc2626', 128: '#facc15',
            256: '#84cc16', 512: '#22c55e', 1024: '#06b6d4', 2048: '#8b5cf6',
        };
        const FG: Record<number, string> = {
            2: '#7c2d12', 4: '#7c2d12', 8: '#fff', 16: '#fff', 32: '#fff',
            64: '#fff', 128: '#fff', 256: '#fff', 512: '#fff', 1024: '#fff', 2048: '#fff',
        };

        let grid: number[][];
        let score = 0, best = +(localStorage.getItem('2048_best') || 0), over = false;
        let hud: any;

        function newTile(): void {
            const empties: Array<[number, number]> = [];
            for (let r = 0; r < SIZE; r++)
                for (let c = 0; c < SIZE; c++)
                    if (!grid[r][c]) empties.push([r, c]);
            if (!empties.length) return;
            const [r, c] = empties[Math.floor(Math.random() * empties.length)];
            grid[r][c] = Math.random() < 0.9 ? 2 : 4;
        }

        function init(): void {
            grid = Array.from({ length: SIZE }, () => Array(SIZE).fill(0));
            score = 0; over = false;
            newTile(); newTile();
            hud.setScore(score);
            hud.setStatus(`最佳: ${best}`);
        }

        function rotate(g: number[][]): number[][] {
            const n = g.length;
            const r: number[][] = Array.from({ length: n }, () => Array(n).fill(0));
            for (let i = 0; i < n; i++)
                for (let j = 0; j < n; j++)
                    r[j][n - 1 - i] = g[i][j];
            return r;
        }

        function move(dir: number): boolean {
            if (over) return false;
            let rotated = grid;
            for (let i = 0; i < dir; i++) rotated = rotate(rotated);
            let changed = false, gained = 0;
            const merged: boolean[][] = Array.from({ length: SIZE }, () => Array(SIZE).fill(false));

            for (let r = 0; r < SIZE; r++) {
                let row: number[] = rotated[r].filter(v => v !== 0);
                for (let i = 0; i < row.length - 1; i++) {
                    if (row[i] === row[i + 1]) {
                        row[i] *= 2;
                        gained += row[i];
                        if (row[i] === 2048) hud.setStatus('🎉 达成 2048！');
                        row.splice(i + 1, 1);
                    }
                }
                while (row.length < SIZE) row.push(0);
                for (let c = 0; c < SIZE; c++) {
                    if (rotated[r][c] !== row[c]) changed = true;
                    rotated[r][c] = row[c];
                }
            }
            for (let i = 0; i < (4 - dir) % 4; i++) rotated = rotate(rotated);
            grid = rotated;

            if (changed) {
                newTile();
                score += gained;
                hud.setScore(score);
                if (score > best) {
                    best = score;
                    localStorage.setItem('2048_best', String(best));
                    hud.setStatus(`最佳: ${best}`);
                }
                if (isOver()) {
                    over = true;
                    setTimeout(() => hud.setStatus('💀 游戏结束 - 点击重开'), 200);
                }
            }
            return changed;
        }

        function isOver(): boolean {
            for (let r = 0; r < SIZE; r++)
                for (let c = 0; c < SIZE; c++) {
                    if (!grid[r][c]) return false;
                    if (r < SIZE - 1 && grid[r][c] === grid[r + 1][c]) return false;
                    if (c < SIZE - 1 && grid[r][c] === grid[r][c + 1]) return false;
                }
            return true;
        }

        function roundRect(c: CanvasRenderingContext2D, x: number, y: number, ww: number, hh: number, rr: number): void {
            c.beginPath();
            c.moveTo(x + rr, y);
            c.arcTo(x + ww, y, x + ww, y + hh, rr);
            c.arcTo(x + ww, y + hh, x, y + hh, rr);
            c.arcTo(x, y + hh, x, y, rr);
            c.arcTo(x, y, x + ww, y, rr);
            c.closePath();
        }

        function render(): void {
            ctx.fillStyle = '#0a0e27';
            ctx.fillRect(0, 0, w, h);
            for (let r = 0; r < SIZE; r++) {
                for (let c = 0; c < SIZE; c++) {
                    const v = grid[r][c];
                    const x = offX + c * TILE, y = offY + r * TILE;
                    ctx.fillStyle = COLORS[v] || '#8b5cf6';
                    const m = TILE * 0.05;
                    roundRect(ctx, x + m, y + m, TILE - m * 2, TILE - m * 2, 8);
                    ctx.fill();
                    if (v) {
                        ctx.fillStyle = FG[v] || '#fff';
                        ctx.font = `bold ${v >= 1000 ? TILE * 0.25 : TILE * 0.35}px sans-serif`;
                        ctx.textAlign = 'center';
                        ctx.textBaseline = 'middle';
                        ctx.fillText(String(v), x + TILE / 2, y + TILE / 2);
                    }
                }
            }
            requestAnimationFrame(render);
        }

        hud = Games.HUD(parent);
        hud.on('restart', init);
        hud.on('pause', () => {});
        Games.keys.on('2048', (k: string, down: boolean) => {
            if (!down || Games.pauseToggle.get()) return;
            const map: Record<string, number> = { ArrowLeft: 3, ArrowRight: 1, ArrowUp: 0, ArrowDown: 2 };
            if (map[k] !== undefined) move(map[k]);
        });
        Games.touchArrows(canvas, (dir: string) => {
            const map: Record<string, number> = { left: 3, right: 1, up: 0, down: 2 };
            if (map[dir] !== undefined) move(map[dir]);
        });

        init();
        render();
    };
})();