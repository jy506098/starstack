// pages/game_center.ts — buy games + launch.

document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll<HTMLFormElement>('form[action="/buy_game"]').forEach(f => {
        f.addEventListener('submit', () => { /* form submits naturally */ });
    });
});