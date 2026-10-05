// games/_framework.ts — shared canvas/HUD/keys/touch helpers.
// Attaches to window.Games so each game file (also compiled as IIFE) can use it.
// Must be loaded BEFORE every game file and BEFORE dispatcher.js.

(function (): void {
    const w = window as any;
    const Games = (w.Games = w.Games || {});

    // fitCanvas — auto-size canvas to parent container.
    Games.fitCanvas = function (canvas: HTMLCanvasElement, aspect: number) {
        aspect = aspect || 4 / 3;
        const parent = canvas.parentElement!;
        const maxW = Math.min(parent.clientWidth - 40, 800);
        let cw = maxW;
        let ch = Math.round(cw / aspect);
        const maxH = Math.min(window.innerHeight - 280, 600);
        if (ch > maxH) { ch = maxH; cw = Math.round(ch * aspect); }
        canvas.style.width = cw + 'px';
        canvas.style.height = ch + 'px';
        canvas.width = cw;
        canvas.height = ch;
        return { w: cw, h: ch };
    };

    // HUD — score + status + pause/restart buttons.
    Games.HUD = function (parent: HTMLElement, opts: any = {}) {
        const bar = document.createElement('div');
        bar.className = 'game-hud';
        bar.style.cssText = 'display:flex;justify-content:space-between;align-items:center;gap:.5rem;padding:.5rem 1rem;background:rgba(0,0,0,.7);color:#fff;border-radius:6px;margin-bottom:.5rem;font-family:monospace;flex-wrap:wrap;';
        bar.innerHTML = `
            <span class="hud-score" style="color:#66fcf1;font-weight:bold;">${opts.scoreLabel || '得分'}: 0</span>
            <span class="hud-status" style="color:#fbbf24;"></span>
            <div class="hud-actions" style="display:flex;gap:.25rem;">
                <button class="hud-btn" data-act="pause" style="padding:.3rem .7rem;border-radius:4px;border:1px solid #66fcf1;background:transparent;color:#66fcf1;cursor:pointer;">暂停</button>
                <button class="hud-btn" data-act="restart" style="padding:.3rem .7rem;border-radius:4px;border:1px solid #f72585;background:transparent;color:#f72585;cursor:pointer;">重开</button>
            </div>`;
        parent.insertBefore(bar, parent.firstChild);
        return {
            el: bar,
            setScore(v: number) { bar.querySelector('.hud-score')!.textContent = (opts.scoreLabel || '得分') + ': ' + v; },
            setStatus(html: string) { bar.querySelector('.hud-status')!.innerHTML = html; },
            on(name: string, cb: () => void) { bar.querySelector(`[data-act="${name}"]`)!.addEventListener('click', cb); },
        };
    };

    // Keys — global keydown/keyup router; each game registers its own handler.
    Games.keys = (() => {
        const map = new Map<string, (k: string, down: boolean) => void>();
        const state: Record<string, boolean> = {};
        window.addEventListener('keydown', (e: KeyboardEvent) => {
            state[e.key] = true;
            for (const h of map.values()) h(e.key, true);
            if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', ' '].includes(e.key)) e.preventDefault();
        });
        window.addEventListener('keyup', (e: KeyboardEvent) => {
            state[e.key] = false;
            for (const h of map.values()) h(e.key, false);
        });
        return {
            is: (k: string) => !!state[k],
            on: (name: string, fn: (k: string, down: boolean) => void) => { map.set(name, fn); },
            off: (name: string) => { map.delete(name); },
        };
    })();

    // touchArrows — translate touch swipes into direction callbacks.
    Games.touchArrows = function (canvas: HTMLCanvasElement, callback: (dir: string) => void) {
        let sx: number | null = null, sy: number | null = null;
        canvas.addEventListener('touchstart', (e: TouchEvent) => {
            const t = e.touches[0]; sx = t.clientX; sy = t.clientY;
        }, { passive: true });
        canvas.addEventListener('touchend', (e: TouchEvent) => {
            if (sx === null || sy === null) return;
            const t = e.changedTouches[0];
            const dx = t.clientX - sx, dy = t.clientY - sy;
            const T = 25;
            if (Math.abs(dx) > Math.abs(dy)) {
                if (dx > T) callback('right');
                else if (dx < -T) callback('left');
            } else {
                if (dy > T) callback('down');
                else if (dy < -T) callback('up');
            }
            sx = sy = null;
        });
    };

    // pauseToggle — shared pause state across games.
    Games.pauseToggle = (function () {
        let paused = false;
        return {
            get: () => paused,
            toggle: () => { paused = !paused; return paused; },
        };
    })();
})();