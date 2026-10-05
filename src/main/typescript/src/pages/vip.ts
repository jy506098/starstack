// pages/vip.ts — buy VIP packages.

document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll<HTMLFormElement>('form[action="/buy_vip"]').forEach(f => {
        f.addEventListener('submit', () => { /* form submits naturally */ });
    });
});