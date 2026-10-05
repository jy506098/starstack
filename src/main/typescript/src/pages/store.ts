// pages/store.ts — buy items + buy VIP packages.

document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll<HTMLFormElement>('form[action="/buy"]').forEach(f => {
        f.addEventListener('submit', e => { /* form submits naturally */ });
    });
    document.querySelectorAll<HTMLFormElement>('form[action="/buy_vip"]').forEach(f => {
        f.addEventListener('submit', e => { /* form submits naturally */ });
    });
});