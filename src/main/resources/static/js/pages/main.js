// pages/main.ts — global page script: clock, mouse trail, nav dropdown, flash fade.
// Imported as ES module from board.html / message_board.html.
import { updateNavClock } from '../common/clock.js';
import { initMouseTrail } from '../mouse-trail/index.js';
import { initNav } from '../common/nav.js';
function loadMouseConfig() {
    return fetch('/get_mouse_config')
        .then(r => r.ok ? r.json() : null)
        .then((c) => c)
        .catch(() => null);
}
function autoFadeFlashes() {
    document.querySelectorAll('.flash').forEach(el => {
        setTimeout(() => {
            el.style.transition = 'opacity .5s';
            el.style.opacity = '0';
            setTimeout(() => el.remove(), 500);
        }, 4000);
    });
}
function initMain() {
    updateNavClock();
    setInterval(updateNavClock, 1000);
    const canvas = document.getElementById('mouseTrailCanvas');
    if (canvas && !canvas.classList.contains('hidden')) {
        loadMouseConfig().then(cfg => {
            if (cfg)
                initMouseTrail(canvas, cfg);
            else
                initMouseTrail(canvas);
        });
    }
    initNav();
    autoFadeFlashes();
}
initMain();
//# sourceMappingURL=main.js.map