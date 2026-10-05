// Typed fetch wrapper. All POST endpoints used by the app share this shape.

export interface ApiError extends Error {
    status: number;
    payload?: unknown;
}

async function apiFetch<T>(url: string, init: RequestInit = {}): Promise<T> {
    const opts: RequestInit = {
        credentials: 'same-origin',
        headers: {
            'Content-Type': 'application/json',
            ...(init.headers ?? {}),
        },
        ...init,
    };
    const r = await fetch(url, opts);
    const text = await r.text();
    let payload: unknown = null;
    try {
        payload = text ? JSON.parse(text) : null;
    } catch {
        // ignore — payload stays null
    }
    if (!r.ok) {
        const err: ApiError = Object.assign(
            new Error(`HTTP ${r.status}`),
            { status: r.status, payload },
        );
        throw err;
    }
    return payload as T;
}

export const api = {
    get: <T>(url: string) => apiFetch<T>(url, { method: 'GET' }),
    post: <T>(url: string, body?: unknown) =>
        apiFetch<T>(url, {
            method: 'POST',
            body: body !== undefined ? JSON.stringify(body) : undefined,
        }),
    postForm: <T>(url: string, form: FormData) =>
        apiFetch<T>(url, { method: 'POST', body: form, headers: {} }),
};

export const Endpoints = {
    buy:           '/buy',
    useItem:       '/use_item',
    buyGame:       '/buy_game',
    buyVip:        '/buy_vip',
    vipPayConfirm: (orderId: string) => `/vip_pay_confirm/${orderId}`,
    buyRecharge:   '/buy_recharge',
    rechargePayConfirm: (orderId: string) => `/recharge_pay_confirm/${orderId}`,
    avatarUpload:  '/settings/avatar',
    avatarReset:   '/settings/avatar',
    saveMouseConfig: '/save_mouse_config',
    getMouseConfig:  '/get_mouse_config',
    vipStatus:     '/api/vip_status',
    mcServer:      '/api/mc_server_info',
} as const;