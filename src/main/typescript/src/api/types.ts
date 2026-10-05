// api/types.ts — shared DTOs.

export interface VipStatus {
    isVip: boolean;
    tier?: string;
    daysLeft?: number;
}

export interface BuyResponse {
    ok: boolean;
    msg: string;
    points?: number;
}

export interface Order {
    orderId: string;
    username: string;
    kind: 'vip' | 'recharge';
    pkgKey: string;
    tier: string;
    durationDays: number;
    priceCny: number;
    points: number;
    bonusPoints: number;
    status: 'pending' | 'paid' | 'cancelled' | 'expired';
    createdAt: string;
    paidAt?: string;
}