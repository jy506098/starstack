// DOM helpers used across pages (flash messages, element creation).
export function showFlash(type, msg) {
    const area = document.querySelector('.flash-area') ?? createFlashArea();
    const div = document.createElement('div');
    div.className = `flash flash-${type}`;
    div.textContent = msg;
    area.appendChild(div);
    setTimeout(() => {
        div.style.opacity = '0';
        setTimeout(() => div.remove(), 500);
    }, 3000);
}
export function createFlashArea() {
    const main = document.querySelector('main.container');
    if (!main) {
        throw new Error('No <main class="container"> found for flash area');
    }
    const area = document.createElement('div');
    area.className = 'flash-area';
    main.prepend(area);
    return area;
}
export function $(sel) {
    return document.querySelector(sel);
}
export function $$(sel) {
    return Array.from(document.querySelectorAll(sel));
}
//# sourceMappingURL=dom.js.map