// pages/settings.ts — save mouse trail config.

import type { MouseConfig } from '../mouse-trail/index.js';

function getConfig(): MouseConfig {
    const enabled = (document.getElementById('mouse-enabled') as HTMLInputElement)?.checked ?? true;
    const colorMode = (document.getElementById('mouse-color-mode') as HTMLSelectElement)?.value as MouseConfig['color_mode'];
    const shape = (document.getElementById('mouse-shape') as HTMLSelectElement)?.value as MouseConfig['shape'];
    return { enabled, color_mode: colorMode, shape };
}

function save(): void {
    const cfg = getConfig();
    const params = new URLSearchParams();
    params.set('enabled', String(cfg.enabled));
    params.set('color_mode', cfg.color_mode);
    params.set('shape', cfg.shape);
    fetch('/save_mouse_config', { method: 'POST', body: params })
        .then(r => r.ok ? r.text() : Promise.reject('HTTP ' + r.status))
        .then(() => {
            if (window.updateMouseConfig) window.updateMouseConfig(cfg);
            alert('已保存');
        })
        .catch(() => alert('保存失败'));
}

document.addEventListener('DOMContentLoaded', () => {
    // Hydrate from server config
    fetch('/get_mouse_config').then(r => r.ok ? r.json() : null).then((c: MouseConfig | null) => {
        if (!c) return;
        (document.getElementById('mouse-enabled') as HTMLInputElement | null)?.setAttribute('checked', '');
        const cm = document.getElementById('mouse-color-mode') as HTMLSelectElement | null;
        if (cm && c.color_mode) cm.value = c.color_mode;
        const sh = document.getElementById('mouse-shape') as HTMLSelectElement | null;
        if (sh && c.shape) sh.value = c.shape;
    }).catch(() => {});

    document.getElementById('save-mouse-config')?.addEventListener('click', save);
});