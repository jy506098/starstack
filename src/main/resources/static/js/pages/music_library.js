"use strict";
// pages/music_library.ts — library browser for music tracks.
document.addEventListener('DOMContentLoaded', () => {
    const audio = document.getElementById('audio-player');
    const title = document.getElementById('track-title');
    const status = document.getElementById('track-status');
    document.querySelectorAll('#track-list li').forEach(li => {
        li.style.cursor = 'pointer';
        li.addEventListener('click', () => {
            const url = li.dataset.url;
            if (!url || !audio)
                return;
            audio.src = url;
            audio.play().catch(() => { });
            if (title)
                title.textContent = li.querySelector('span')?.textContent ?? '';
            if (status)
                status.textContent = '▶ 播放中';
        });
    });
    audio?.addEventListener('ended', () => {
        if (status)
            status.textContent = '已结束';
    });
});
//# sourceMappingURL=music_library.js.map