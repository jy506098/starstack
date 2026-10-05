-- StarStack SQLite schema. Idempotent — safe to run on every boot.
-- Note: PRAGMA statements run via JDBC DataSource init, not here.

CREATE TABLE IF NOT EXISTS users (
    username              TEXT PRIMARY KEY,
    password_hash         TEXT NOT NULL,
    password_format       TEXT NOT NULL DEFAULT 'bcrypt',
    phone                 TEXT NOT NULL DEFAULT '',
    points                INTEGER NOT NULL DEFAULT 100,
    total_spent           INTEGER NOT NULL DEFAULT 0,
    inventory_json        TEXT NOT NULL DEFAULT '{}',
    unlocked_content_json  TEXT NOT NULL DEFAULT '[]',
    effects_json          TEXT NOT NULL DEFAULT '{}',
    is_admin              INTEGER NOT NULL DEFAULT 0,
    admin_daily_points_date  TEXT NOT NULL DEFAULT '',
    admin_daily_points_count INTEGER NOT NULL DEFAULT 0,
    mouse_config_json     TEXT NOT NULL DEFAULT '{"enabled":true,"color_mode":"rainbow","shape":"circle"}',
    avatar                TEXT NOT NULL DEFAULT 'default:none',
    vip_tier              TEXT NOT NULL DEFAULT '',
    vip_expires_at        TEXT NOT NULL DEFAULT '',
    last_vip_bonus_date   TEXT NOT NULL DEFAULT '',
    vip_pending_order_id  TEXT NOT NULL DEFAULT '',
    vip_purchase_history_json TEXT NOT NULL DEFAULT '[]',
    recharge_history_json TEXT NOT NULL DEFAULT '[]',
    fixed_tasks_json      TEXT NOT NULL DEFAULT '{}',
    daily_tasks_json      TEXT NOT NULL DEFAULT '{}',
    daily_visit_count     INTEGER NOT NULL DEFAULT 0,
    last_visit_date       TEXT NOT NULL DEFAULT '',
    last_post_date        TEXT NOT NULL DEFAULT '',
    created_at            TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at            TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_users_vip_expires ON users(vip_expires_at);

CREATE TABLE IF NOT EXISTS messages (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    name       TEXT NOT NULL,
    msg        TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_messages_created ON messages(created_at DESC);

CREATE TABLE IF NOT EXISTS orders (
    order_id       TEXT PRIMARY KEY,
    username       TEXT NOT NULL REFERENCES users(username) ON DELETE CASCADE,
    kind           TEXT NOT NULL,
    pkg_key        TEXT NOT NULL,
    tier           TEXT NOT NULL DEFAULT '',
    duration_days  INTEGER NOT NULL DEFAULT 0,
    price_cny      INTEGER NOT NULL,
    points         INTEGER NOT NULL DEFAULT 0,
    bonus_points   INTEGER NOT NULL DEFAULT 0,
    status         TEXT NOT NULL DEFAULT 'pending',
    created_at     TEXT NOT NULL DEFAULT (datetime('now')),
    paid_at        TEXT
);
CREATE INDEX IF NOT EXISTS idx_orders_user_status ON orders(username, status);

CREATE TABLE IF NOT EXISTS avatars (
    filename    TEXT PRIMARY KEY,
    username    TEXT NOT NULL,
    uploaded_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_avatars_user ON avatars(username);