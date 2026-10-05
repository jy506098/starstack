// Live nav clock (renders HH:MM:SS into #navClock). No-op if element missing.

function pad(n: number): string {
    return n < 10 ? '0' + n : String(n);
}

export function initClock(): void {
    const el = document.getElementById('navClock');
    if (!el) return;
    const tick = () => {
        const d = new Date();
        el.textContent = `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
    };
    tick();
    setInterval(tick, 1000);
}