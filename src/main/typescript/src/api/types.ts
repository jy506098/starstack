// StarStack DTO types shared between frontend and (via JSON over HTTP) backend.

export interface User {
    username: string;
    points: number;
    unlockedContent: string[];
    effects: Record<string, boolean>;
    isAdmin: boolean;
    isVip: boolean;
    vipTier: string;
    vipDaysLeft: number;
    vipExpiresAt: string;
    avatar: string;
}

export interface BuyRequest {
    item: string;
}

export interface BuyResponse {
    success: boolean;
    msg: string;
    new_points?: number;
    inventory?: Record<string, number>;
    redirect_url?: string;
}

export interface VipStatusResponse {
    isVip: boolean;
    tier: string;
    daysLeft: number;
    expiresAt: string;
}

export interface VipPackage {
    key: string;
    tier: string;
    label: string;
    durationDays: number;
    priceCny: number;
    dailyBonus: number;
}

export interface RechargePackage {
    key: string;
    label: string;
    priceCny: number;
    points: number;
    bonusPoints: number;
}

export interface McServerConfig {
    host: string;
    port: number;
    version: string;
    motd: string;
}

export interface McServerResponse {
    success: boolean;
    config?: McServerConfig;
    msg?: string;
}

export interface MouseConfig {
    enabled: boolean;
    color_mode: 'rainbow' | 'cyan' | 'random';
    shape: 'circle' | 'square' | 'star';
}

export interface SnakeState {
    type: 'state';
    room: string;
    w: number;
    h: number;
    foods: [number, number][];
    players: SnakePlayer[];
    ts: number;
}

export interface SnakePlayer {
    name: string;
    color: string;
    score: number;
    alive: boolean;
    snake: [number, number][];
}

export interface SnakeWelcome {
    type: 'welcome';
    you: string;
    color: string;
    w: number;
    h: number;
}

export interface SnakeGameOver {
    type: 'gameover';
    winner: string;
}

export interface SnakeError {
    type: 'error';
    msg: string;
}

export type SnakeServerMessage = SnakeState | SnakeWelcome | SnakeGameOver | SnakeError;