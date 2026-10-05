"use strict";
// games/dispatcher.ts — global dispatcher that routes a gameName to the
// appropriate IIFE-registered game.
(function () {
    const w = window;
    w.Games = w.Games || {};
    const dispatcher = (canvas, status, ...rest) => {
        const gameName = rest[0];
        const fn = w.Games[gameName];
        if (!fn) {
            status.textContent = `游戏 ${gameName} 暂未实现`;
            return;
        }
        fn(canvas, status);
    };
    w.Games['__dispatcher__'] = dispatcher;
})();
//# sourceMappingURL=dispatcher.js.map