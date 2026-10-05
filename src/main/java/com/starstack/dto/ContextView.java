package com.starstack.dto;

import com.starstack.model.MouseEffectConfig;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Global template context — populated by {@code GlobalModelAdvice} on every
 * request. Mirrors the Python {@code @app.context_processor} data: username,
 * points, VIP, effects, etc.
 *
 * Thymeleaf templates reference fields directly via ${ctx.fieldName}.
 */
@Data
public class ContextView {
    private String username;
    private long userPoints;
    private String avatar = "default:none";
    private boolean isVip;
    private String vipTier = "";
    private int vipDaysLeft;
    private String vipExpiresAtStr = "";
    private List<String> unlockedContent = new ArrayList<>();
    private Map<String, Boolean> effects = new LinkedHashMap<>();
    private boolean hasCyberTshirt;
    private boolean hasClock;
    private boolean mouseEffectEnabled;
    private MouseEffectConfig mouseConfig = new MouseEffectConfig();
    /** Map from tier name → {daily_bonus, daily_bonus_str} for VIP perks display. */
    private Map<String, Map<String, Object>> vipTiers = new LinkedHashMap<>();
    /** MC server config (always available; for VIP-only viewing). */
    private MCServerConfig mcServer = new MCServerConfig();
    private List<Map<String, Object>> flashes = new ArrayList<>();

    @Data
    public static class MCServerConfig {
        private String host = "starstack.example.com";
        private int port = 25565;
        private String version = "1.20.4";
        private String motd = "⭐ StarStack VIP 专属服务器";
    }
}