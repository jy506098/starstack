"use strict";
// games/_framework.ts — shared canvas helpers for the IIFE game bundle.
// Each game registers itself on window.Games[name]; play_game.html's
// dispatcher reads data-game attribute on its <script> tag and invokes
// the right entry point.
Object.defineProperty(exports, "__esModule", { value: true });
exports.fitCanvas = fitCanvas;
exports.makeHud = makeHud;
(function bootstrapFramework() {
    window.Games = {};
})();
function fitCanvas(canvas, opts = {}) {
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
function makeHud(canvas) {
    let hud = canvas.parentElement?.querySelector('.game-hud');
    if (hud)
        return hud;
    hud = document.createElement('div');
    hud.className = 'game-hud';
    canvas.parentElement?.insertBefore(hud, canvas);
    return hud;
}
//# sourceMappingURL=_framework.js.map