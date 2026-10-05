// api/client.ts — typed fetch wrappers.

import type { VipStatus } from './types.js';

export async function getVipStatus(): Promise<VipStatus> {
    const r = await fetch('/api/vip_status');
    if (!r.ok) throw new Error(`HTTP ${r.status}`);
    return r.json();
}

export async function postJson<TReq, TRes>(url: string, body: TReq): Promise<TRes> {
    const r = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    });
    if (!r.ok) throw new Error(`HTTP ${r.status}`);
    return r.json();
}

export async function postForm<TRes>(url: string, form: HTMLFormElement): Promise<TRes> {
    const fd = new FormData(form);
    const r = await fetch(url, { method: 'POST', body: fd });
    if (!r.ok) throw new Error(`HTTP ${r.status}`);
    return r.json();
}