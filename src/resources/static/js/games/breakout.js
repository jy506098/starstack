"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const breakout = (canvas) => {
    const ctx = canvas.getContext('2d');
    if (!ctx)
        return { stop: () => { } };
    ctx.fillStyle = '#000';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.fillStyle = '#0ff';
    ctx.font = '20px monospace';
    ctx.fillText('打砖块 — 待实现 (Phase O)', 20, 40);
    return { stop: () => { } };
};
(function register() {
    window.Games['打砖块'] = breakout;
})();
//# sourceMappingURL=breakout.js.map