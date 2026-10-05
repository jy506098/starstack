package com.starstack.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/** VIP / 充值 order. Pending → paid (or cancelled/expired). */
@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
public class Order {

    @Id
    @Column(name = "order_id", length = 32)
    private String orderId;

    @Column(nullable = false, length = 64)
    private String username;

    /** "vip" or "recharge" */
    @Column(nullable = false)
    private String kind;

    @Column(name = "pkg_key", nullable = false)
    private String pkgKey;

    @Column(nullable = false)
    private String tier = "";

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Column(name = "price_cny", nullable = false)
    private int priceCny;

    @Column(nullable = false)
    private int points;

    @Column(name = "bonus_points", nullable = false)
    private int bonusPoints;

    /** "pending" / "paid" / "cancelled" / "expired" */
    @Column(nullable = false)
    private String status = "pending";

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    @Column(name = "paid_at")
    private String paidAt;
}