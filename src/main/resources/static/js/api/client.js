// api/client.ts — typed fetch wrappers.
export async function getVipStatus() {
    const r = await fetch('/api/vip_status');
    if (!r.ok)
        throw new Error(`HTTP ${r.status}`);
    return r.json();
}
export async function postJson(url, body) {
    const r = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    });
    if (!r.ok)
        throw new Error(`HTTP ${r.status}`);
    return r.json();
}
export async function postForm(url, form) {
    const fd = new FormData(form);
    const r = await fetch(url, { method: 'POST', body: fd });
    if (!r.ok)
        throw new Error(`HTTP ${r.status}`);
    return r.json();
}
//# sourceMappingURL=client.js.map