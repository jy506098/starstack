// pages/inventory.ts — use item from inventory.

document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll<HTMLFormElement>('form[action="/use_item"]').forEach(f => {
        f.addEventListener('submit', () => { /* form submits naturally */ });
    });
});