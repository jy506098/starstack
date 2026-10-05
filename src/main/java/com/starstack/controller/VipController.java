package com.starstack.controller;

import com.starstack.model.Order;
import com.starstack.model.User;
import com.starstack.model.VipPurchase;
import com.starstack.repository.OrderRepository;
import com.starstack.repository.UserRepository;
import com.starstack.service.CatalogData;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/** VIP purchase + payment confirmation. */
@Controller
public class VipController {

    private final UserRepository users;
    private final OrderRepository orders;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    public VipController(UserRepository users, OrderRepository orders) {
        this.users = users;
        this.orders = orders;
    }

    @GetMapping("/vip")
    public String vipPage(HttpSession session, Model model) {
        String u = (String) session.getAttribute("username");
        if (u == null) return "redirect:/login";
        model.addAttribute("packages", CatalogData.VIP_PACKAGES);
        return "vip";
    }

    @PostMapping("/buy_vip")
    public String buyVip(@RequestParam String pkg,
                         HttpSession session,
                         RedirectAttributes flash2) {
        String username = (String) session.getAttribute("username");
        if (username == null) return "redirect:/login";
        Map<String, Object> meta = CatalogData.VIP_PACKAGES.get(pkg);
        if (meta == null) {
            flash2.addFlashAttribute("error", "套餐不存在");
            return "redirect:/vip";
        }
        User u = users.findById(username).orElse(null);
        if (u == null) return "redirect:/login";
        Order o = new Order();
        o.setOrderId(UUID.randomUUID().toString().substring(0, 12));
        o.setUsername(username);
        o.setKind("vip");
        o.setPkgKey(pkg);
        o.setTier((String) meta.get("tier"));
        o.setDurationDays((int) meta.get("duration_days"));
        o.setPriceCny((int) meta.get("price_cny"));
        o.setPoints(0);
        o.setBonusPoints(0);
        o.setStatus("pending");
        o.setCreatedAt(LocalDateTime.now().toString());
        orders.save(o);
        u.setVipPendingOrderId(o.getOrderId());
        users.save(u);
        return "redirect:/vip_pay/" + o.getOrderId();
    }

    @GetMapping("/vip_pay/{orderId}")
    public String vipPay(@PathVariable String orderId,
                         HttpSession session,
                         Model model,
                         RedirectAttributes flash2) {
        String username = (String) session.getAttribute("username");
        if (username == null) return "redirect:/login";
        Order o = orders.findById(orderId).orElse(null);
        if (o == null || !o.getUsername().equals(username)) {
            flash2.addFlashAttribute("error", "订单不存在");
            return "redirect:/vip";
        }
        model.addAttribute("order", o);
        model.addAttribute("pkgLabel", CatalogData.VIP_PACKAGES.getOrDefault(o.getPkgKey(), Map.of()).get("label"));
        return "vip_pay";
    }

    @GetMapping("/vip_pay_confirm/{orderId}")
    public String vipConfirm(@PathVariable String orderId,
                             HttpSession session,
                             RedirectAttributes flash2) {
        String username = (String) session.getAttribute("username");
        if (username == null) return "redirect:/login";
        Order o = orders.findById(orderId).orElse(null);
        if (o == null || !o.getUsername().equals(username)) {
            flash2.addFlashAttribute("error", "订单不存在");
            return "redirect:/vip";
        }
        if (!"pending".equals(o.getStatus())) {
            flash2.addFlashAttribute("error", "订单状态异常");
            return "redirect:/vip";
        }
        User u = users.findById(username).orElse(null);
        if (u == null) return "redirect:/login";
        // 模拟支付成功: 累加 VIP 天数
        LocalDateTime expiresAt;
        if (u.getVipExpiresAt() != null && !u.getVipExpiresAt().isEmpty()) {
            try {
                expiresAt = LocalDateTime.parse(u.getVipExpiresAt(), FMT);
                if (expiresAt.isBefore(LocalDateTime.now())) expiresAt = LocalDateTime.now();
            } catch (Exception e) { expiresAt = LocalDateTime.now(); }
        } else {
            expiresAt = LocalDateTime.now();
        }
        expiresAt = expiresAt.plusDays(o.getDurationDays());
        u.setVipTier(o.getTier());
        u.setVipExpiresAt(expiresAt.format(FMT));
        u.setVipPendingOrderId("");
        // Append receipt
        VipPurchase vp = new VipPurchase();
        vp.setOrderId(o.getOrderId());
        vp.setPkgKey(o.getPkgKey());
        vp.setTier(o.getTier());
        vp.setDurationDays(o.getDurationDays());
        vp.setPriceCny(o.getPriceCny());
        vp.setPoints(0);
        vp.setPaidAt(LocalDateTime.now().toString());
        if (u.getVipPurchaseHistory() == null) u.setVipPurchaseHistory(new java.util.ArrayList<>());
        u.getVipPurchaseHistory().add(vp);
        users.save(u);
        o.setStatus("paid");
        o.setPaidAt(LocalDateTime.now().toString());
        orders.save(o);
        flash2.addFlashAttribute("success", "VIP 已开通 " + o.getTier() + " · " + o.getDurationDays() + " 天");
        return "redirect:/vip";
    }
}