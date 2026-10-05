// games/dispatcher.ts — global dispatcher that routes a gameName to the
// appropriate IIFE-registered game.

interface GameFn {
    (canvas: HTMLCanvasElement, status: HTMLElement, ...rest: unknown[]): void;
}

(function (): void {
    const w = window as unknown as { Games: Record<string, GameFn> };
    w.Games = w.Games || {};
    const dispatcher: GameFn = (canvas, status, ...rest) => {
        const gameName = rest[0] as string;
        const fn = w.Games[gameName];
        if (!fn) {
            status.textContent = `游戏 ${gameName} 暂未实现`;
            return;
        }
        fn(canvas, status);
    };
    w.Games['__dispatcher__'] = dispatcher;
})();