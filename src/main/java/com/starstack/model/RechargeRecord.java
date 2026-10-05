package com.starstack.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Recharge (积分充值) receipt entry on User.recharge_history_json. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RechargeRecord {
    private String pkgKey;
    private int priceCny;
    private int points;
    private int bonus;
    private String paidAt;
    private String orderId;
}