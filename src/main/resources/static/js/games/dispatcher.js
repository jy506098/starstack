"use strict";
// games/dispatcher.ts — invoked from <script> tag with data-game attribute.
// Reads its own data-game and dispatches to the right game module.
(function dispatcher() {
    const script = document.currentScript;
    const gameName = script?.dataset.game ?? '';
    const canvas = document.getElementById('gameCanvas');
    if (!canvas) {
        console.warn('[dispatcher] #gameCanvas not found');
        return;
    }
    if (!gameName) {
        alert('未知游戏：未指定 data-game');
        return;
    }
    const games = window.Games;
    const entry = games?.[gameName];
    if (!entry) {
        alert(`未知游戏：${gameName}`);
        return;
    }
    const inst = entry(canvas);
    if (inst && typeof inst.start === 'function') {
        inst.start();
    }
})();
//# sourceMappingURL=dispatcher.js.map