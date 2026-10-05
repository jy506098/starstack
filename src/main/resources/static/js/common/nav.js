// common/nav.ts — navbar dropdown toggle + click-outside + Esc close.
function closeAllDropdowns(except) {
    document.querySelectorAll('.nav-user-dropdown.open').forEach(d => {
        if (d === except)
            return;
        d.classList.remove('open');
        const t = d.querySelector('.nav-user-trigger');
        t?.setAttribute('aria-expanded', 'false');
    });
}
export function initNav() {
    document.querySelectorAll('.nav-user-trigger').forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            const dd = btn.closest('.nav-user-dropdown');
            if (!dd)
                return;
            const willOpen = !dd.classList.contains('open');
            closeAllDropdowns(dd);
            dd.classList.toggle('open', willOpen);
            btn.setAttribute('aria-expanded', willOpen ? 'true' : 'false');
        });
    });
    document.addEventListener('click', (e) => {
        if (!e.target.closest('.nav-user-dropdown')) {
            closeAllDropdowns(null);
        }
    });
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape')
            closeAllDropdowns(null);
    });
}
//# sourceMappingURL=nav.js.map