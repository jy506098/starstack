// Nav dropdown logic (avatar/account button -> menu). Click-outside + Esc close.

function closeAllDropdowns(except: HTMLElement | null): void {
    document.querySelectorAll<HTMLElement>('.nav-user-dropdown.open').forEach(d => {
        if (d === except) return;
        d.classList.remove('open');
        const t = d.querySelector<HTMLButtonElement>('.nav-user-trigger');
        t?.setAttribute('aria-expanded', 'false');
    });
}

function bindTrigger(btn: HTMLButtonElement): void {
    btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const dd = btn.closest<HTMLElement>('.nav-user-dropdown');
        if (!dd) return;
        const willOpen = !dd.classList.contains('open');
        closeAllDropdowns(dd);
        dd.classList.toggle('open', willOpen);
        btn.setAttribute('aria-expanded', willOpen ? 'true' : 'false');
    });
}

export function initNav(): void {
    document.querySelectorAll<HTMLButtonElement>('.nav-user-trigger').forEach(bindTrigger);
    document.addEventListener('click', (e) => {
        if (!(e.target as HTMLElement).closest('.nav-user-dropdown')) {
            closeAllDropdowns(null);
        }
    });
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') closeAllDropdowns(null);
    });
}