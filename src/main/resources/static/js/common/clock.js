// common/clock.ts — updates the navbar clock.
export function updateNavClock() {
    const el = document.getElementById('navClock');
    if (!el)
        return;
    const now = new Date();
    const pad = (n) => String(n).padStart(2, '0');
    el.textContent = `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`;
}
//# sourceMappingURL=clock.js.map