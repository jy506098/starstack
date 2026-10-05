// games/_framework.ts — shared canvas helpers for the IIFE game bundle.
// Each game registers itself on window.Games[name]; play_game.html's
// dispatcher reads data-game attribute on its <script> tag and invokes
// the right entry point.

(function bootstrapFramework() {
    (window as unknown as { Games: Record<string, unknown> }).Games = {};
})();

export type GameEntry = (canvas: HTMLCanvasElement, opts?: Record<string, unknown>) => GameInstance;

export interface GameInstance {
    start?: () => void;
    stop?: () => void;
    destroy?: () => void;
}

export interface FitOpts {
    padX?: number;
    padY?: number;
    bg?: string;
}

export function fitCanvas(canvas: HTMLCanvasElement, opts: FitOpts = {}): void {
    const dpr = window.devicePixelRatio || 1;
    const rect = canvas.getBoundingClientRect();
    canvas.width = Math.floor(rect.width * dpr);
    canvas.height = Math.floor(rect.height * dpr);
    const ctx = canvas.getContext('2d');
    if (ctx && opts.bg) {
        ctx.fillStyle = opts.bg;
        ctx.fillRect(0, 0, canvas.width, canvas.height);
    }
}

export function makeHud(canvas: HTMLCanvasElement): HTMLDivElement {
    let hud = canvas.parentElement?.querySelector<HTMLDivElement>('.game-hud');
    if (hud) return hud;
    hud = document.createElement('div');
    hud.className = 'game-hud';
    canvas.parentElement?.insertBefore(hud, canvas);
    return hud;
}