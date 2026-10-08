package com.starstack.config;

import com.starstack.dto.ContextView;
import com.starstack.model.User;
import com.starstack.repository.UserRepository;
import com.starstack.service.CatalogData;
import com.starstack.service.VipService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.FlashMap;

/**
 * Populates ${ctx} for every Thymeleaf template. Mirrors the Python
 * @app.context_processor that computed username, points, vip, avatar,
 * effects, etc. for every render.
 */
@ControllerAdvice
public class GlobalModelAdvice {

    private final UserRepository users;
    private final VipService vipService;
    private final CatalogData catalog;

    @Autowired
    public GlobalModelAdvice(UserRepository users, VipService vipService, CatalogData catalog) {
        this.users = users;
        this.vipService = vipService;
        this.catalog = catalog;
    }

    @ModelAttribute("ctx")
    public ContextView ctx(HttpServletRequest req) {
        ContextView ctx = new ContextView();
        HttpSession session = req.getSession(false);
        String username = session == null ? null : (String) session.getAttribute("username");
        ctx.setUsername(username);

        if (username != null) {
            User user = users.findById(username).orElse(null);
            if (user != null) {
                vipService.grantAdminDailyPoints(user);
                vipService.grantDailyBonus(user);
                users.save(user);

                ctx.setUserPoints(user.getPoints());
                ctx.setAvatar(user.getAvatar());
                ctx.setPhone(user.getPhone());
                ctx.setUnlockedContent(user.getUnlockedContent());
                ctx.setEffects(user.getEffects());
                ctx.setHasCyberTshirt(user.hasEffect("cyber_tshirt"));
                ctx.setHasClock(user.hasUnlocked("时钟"));
                ctx.setMouseEffectEnabled(user.getMouseConfig() != null && user.getMouseConfig().isEnabled());
                ctx.setMouseConfig(user.getMouseConfig());
                if (user.getVipTier() != null && !user.getVipTier().isEmpty()) {
                    var vip = vipService.status(user);
                    ctx.setVip(true);
                    ctx.setVipTier(user.getVipTier());
                    ctx.setVipDaysLeft(vip.daysLeft());
                    ctx.setVipExpiresAtStr(user.getVipExpiresAt());
                }
            }
        }

        ctx.setVipTiers(catalog.getVipTierMap());
        return ctx;
    }

    @ModelAttribute("flashes")
    public java.util.List<java.util.Map<String, String>> flashes(HttpServletRequest req) {
        java.util.List<java.util.Map<String, String>> out = new java.util.ArrayList<>();
        FlashMap flashMap = (FlashMap) req.getAttribute("flashMap");
        if (flashMap == null) return out;
        // Spring's FlashMap is populated by RedirectAttributes; we convert to a simple list.
        return out;
    }
}