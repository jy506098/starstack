package com.starstack.service;

import com.starstack.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * VIP helpers — port of {@code app/service.py::is_user_vip},
 * {@code grant_admin_daily_points}, {@code grant_vip_daily_bonus}.
 */
@Service
public class VipService {
    private static final Logger LOG = LoggerFactory.getLogger(VipService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** Returns (isVip, tier, expiresAt, daysLeft). */
    public VipStatus status(User user) {
        if (user == null) return new VipStatus(false, "", null, 0);
        String tier = user.getVipTier();
        String exp = user.getVipExpiresAt();
        if (tier == null || tier.isEmpty() || exp == null || exp.isEmpty()) {
            return new VipStatus(false, tier == null ? "" : tier, null, 0);
        }
        LocalDateTime expiresAt;
        try {
            expiresAt = LocalDateTime.parse(exp, FMT);
        } catch (Exception e) {
            return new VipStatus(false, tier, null, 0);
        }
        LocalDateTime now = LocalDateTime.now();
        if (!expiresAt.isAfter(now)) return new VipStatus(false, tier, expiresAt, 0);
        long days = java.time.Duration.between(now, expiresAt).toDays() + 1;
        return new VipStatus(true, tier, expiresAt, (int) Math.max(days, 1));
    }

    public void grantAdminDailyPoints(User user) {
        if (user == null || !user.isAdmin()) return;
        String today = LocalDateTime.now().format(DAY);
        if (today.equals(user.getAdminDailyPointsDate())) return;
        int cap = user.isAdmin() ? 50 : 0;
        if (cap <= 0) return;
        user.setPoints(user.getPoints() + cap);
        user.setAdminDailyPointsDate(today);
        user.setAdminDailyPointsCount(user.getAdminDailyPointsCount() + 1);
        LOG.info("[ADMIN] {} +{} 积分 (今日已发 {} 次)", user.getUsername(), cap, user.getAdminDailyPointsCount());
    }

    public void grantDailyBonus(User user) {
        if (user == null) return;
        VipStatus s = status(user);
        if (!s.isVip()) return;
        String today = LocalDateTime.now().format(DAY);
        if (today.equals(user.getLastVipBonusDate())) return;
        int bonus = (int) CatalogData.VIP_TIERS.getOrDefault(s.tier(), Map.of("daily_bonus", 0)).get("daily_bonus");
        if (bonus <= 0) return;
        user.setPoints(user.getPoints() + bonus);
        user.setLastVipBonusDate(today);
        LOG.info("[VIP] {} ({}) +{} 积分 (剩 {} 天)", user.getUsername(), s.tier(), bonus, s.daysLeft());
    }

    /** Java record-style VIP status. */
    public record VipStatus(boolean isVip, String tier, LocalDateTime expiresAt, int daysLeft) {}
}