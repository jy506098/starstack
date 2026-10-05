// Bootstrap module loaded on every page: clock + nav dropdown + mouse-trail
// + global flash auto-fade. Pages with their own behavior import additional
// modules from src/pages/*.ts.

import { initClock } from '../common/clock';
import { initNav } from '../common/nav';
import { initMouseTrail } from '../mouse-trail';

document.addEventListener('DOMContentLoaded', () => {
    initClock();
    initNav();
    initMouseTrail();

    // Fade out rendered flash messages after a few seconds.
    document.querySelectorAll<HTMLElement>('.flash').forEach(el => {
        setTimeout(() => {
            el.style.opacity = '0';
            setTimeout(() => el.remove(), 500);
        }, 4000);
    });
});