// DOM helpers used across pages (flash messages, element creation).

export function showFlash(type: 'success' | 'error' | 'info', msg: string): void {
    const area = document.querySelector<HTMLElement>('.flash-area') ?? createFlashArea();
    const div = document.createElement('div');
    div.className = `flash flash-${type}`;
    div.textContent = msg;
    area.appendChild(div);
    setTimeout(() => {
        div.style.opacity = '0';
        setTimeout(() => div.remove(), 500);
    }, 3000);
}

export function createFlashArea(): HTMLElement {
    const main = document.querySelector<HTMLElement>('main.container');
    if (!main) {
        throw new Error('No <main class="container"> found for flash area');
    }
    const area = document.createElement('div');
    area.className = 'flash-area';
    main.prepend(area);
    return area;
}

export function $(sel: string): HTMLElement | null {
    return document.querySelector<HTMLElement>(sel);
}

export function $$(sel: string): HTMLElement[] {
    return Array.from(document.querySelectorAll<HTMLElement>(sel));
}