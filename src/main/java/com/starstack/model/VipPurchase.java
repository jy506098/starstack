package com.starstack.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** VIP purchase receipt entry on User.vip_purchase_history_json. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VipPurchase {
    private String pkgKey;
    private String tier;
    private int durationDays;
    private int priceCny;
    private int points;
    private String paidAt;
    private String orderId;
}