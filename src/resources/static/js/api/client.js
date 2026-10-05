// Typed fetch wrapper. All POST endpoints used by the app share this shape.
async function apiFetch(url, init = {}) {
    const opts = {
        credentials: 'same-origin',
        headers: {
            'Content-Type': 'application/json',
            ...(init.headers ?? {}),
        },
        ...init,
    };
    const r = await fetch(url, opts);
    const text = await r.text();
    let payload = null;
    try {
        payload = text ? JSON.parse(text) : null;
    }
    catch {
        // ignore — payload stays null
    }
    if (!r.ok) {
        const err = Object.assign(new Error(`HTTP ${r.status}`), { status: r.status, payload });
        throw err;
    }
    return payload;
}
export const api = {
    get: (url) => apiFetch(url, { method: 'GET' }),
    post: (url, body) => apiFetch(url, {
        method: 'POST',
        body: body !== undefined ? JSON.stringify(body) : undefined,
    }),
    postForm: (url, form) => apiFetch(url, { method: 'POST', body: form, headers: {} }),
};
export const Endpoints = {
    buy: '/buy',
    useItem: '/use_item',
    buyGame: '/buy_game',
    buyVip: '/buy_vip',
    vipPayConfirm: (orderId) => `/vip_pay_confirm/${orderId}`,
    buyRecharge: '/buy_recharge',
    rechargePayConfirm: (orderId) => `/recharge_pay_confirm/${orderId}`,
    avatarUpload: '/settings/avatar',
    avatarReset: '/settings/avatar',
    saveMouseConfig: '/save_mouse_config',
    getMouseConfig: '/get_mouse_config',
    vipStatus: '/api/vip_status',
    mcServer: '/api/mc_server_info',
};
//# sourceMappingURL=client.js.map