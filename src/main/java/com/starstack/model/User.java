package com.starstack.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * StarStack user. Mirrors users.json keys (snake_case columns in DB,
 * camelCase Java fields). Password is hashed with BCrypt for new users
 * and legacy werkzeug-scrypt for migrated users (login rewrites to BCrypt).
 */
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
public class User {

    @Id
    @Column(length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** "bcrypt" (new) or "werkzeug-scrypt" (migrated legacy). */
    @Column(name = "password_format", nullable = false)
    private String passwordFormat = "bcrypt";

    @Column(nullable = false)
    private String phone = "";

    @Column(nullable = false)
    private long points = 100;

    @Column(name = "total_spent", nullable = false)
    private long totalSpent = 0;

    @Convert(converter = JsonStringConverter.class)
    @Column(name = "inventory_json", nullable = false, columnDefinition = "TEXT")
    private Map<String, Integer> inventory = new LinkedHashMap<>();

    @Convert(converter = JsonListStringConverter.class)
    @Column(name = "unlocked_content_json", nullable = false, columnDefinition = "TEXT")
    private java.util.List<String> unlockedContent = new java.util.ArrayList<>();

    @Convert(converter = JsonStringConverter.class)
    @Column(name = "effects_json", nullable = false, columnDefinition = "TEXT")
    private Map<String, Boolean> effects = new LinkedHashMap<>();

    @Column(name = "is_admin", nullable = false)
    private boolean isAdmin;

    @Column(name = "admin_daily_points_date", nullable = false)
    private String adminDailyPointsDate = "";

    @Column(name = "admin_daily_points_count", nullable = false)
    private int adminDailyPointsCount;

    @Convert(converter = MouseEffectConfigConverter.class)
    @Column(name = "mouse_config_json", nullable = false, columnDefinition = "TEXT")
    private MouseEffectConfig mouseConfig = new MouseEffectConfig();

    @Column(nullable = false)
    private String avatar = "default:none";

    @Column(name = "vip_tier", nullable = false)
    private String vipTier = "";

    @Column(name = "vip_expires_at", nullable = false)
    private String vipExpiresAt = "";

    @Column(name = "last_vip_bonus_date", nullable = false)
    private String lastVipBonusDate = "";

    @Column(name = "vip_pending_order_id", nullable = false)
    private String vipPendingOrderId = "";

    @Convert(converter = JsonListStringConverter.class)
    @Column(name = "vip_purchase_history_json", nullable = false, columnDefinition = "TEXT")
    private java.util.List<VipPurchase> vipPurchaseHistory = new java.util.ArrayList<>();

    @Convert(converter = JsonListStringConverter.class)
    @Column(name = "recharge_history_json", nullable = false, columnDefinition = "TEXT")
    private java.util.List<RechargeRecord> rechargeHistory = new java.util.ArrayList<>();

    @Convert(converter = JsonStringConverter.class)
    @Column(name = "fixed_tasks_json", nullable = false, columnDefinition = "TEXT")
    private Map<String, TaskRecord> fixedTasks = new LinkedHashMap<>();

    @Convert(converter = JsonStringConverter.class)
    @Column(name = "daily_tasks_json", nullable = false, columnDefinition = "TEXT")
    private Map<String, String> dailyTasks = new LinkedHashMap<>();

    @Column(name = "daily_visit_count", nullable = false)
    private int dailyVisitCount;

    @Column(name = "last_visit_date", nullable = false)
    private String lastVisitDate = "";

    @Column(name = "last_post_date", nullable = false)
    private String lastPostDate = "";

    @Column(name = "created_at", nullable = false, updatable = false)
    private String createdAt;

    @Column(name = "updated_at", nullable = false)
    private String updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now.toString();
        this.updatedAt = now.toString();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now().toString();
    }

    @JsonIgnore
    public boolean hasUnlocked(String key) {
        return unlockedContent != null && unlockedContent.contains(key);
    }

    @JsonIgnore
    public boolean hasEffect(String key) {
        return effects != null && Boolean.TRUE.equals(effects.get(key));
    }

    /** Counts the user's owned quantity of an item; 0 if absent. */
    @JsonIgnore
    public int inventoryCount(String item) {
        if (inventory == null) return 0;
        Integer n = inventory.get(item);
        return n == null ? 0 : n;
    }
}