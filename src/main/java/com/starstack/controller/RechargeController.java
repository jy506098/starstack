package com.starstack.controller;

import com.starstack.model.Order;
import com.starstack.model.RechargeRecord;
import com.starstack.model.User;
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
import java.util.Map;
import java.util.UUID;

/** Recharge (积分充值) purchase + payment confirmation. */
@Controller
public class RechargeController {

    private final UserRepository users;
    private final OrderRepository orders;

    @Autowired
    public RechargeController(UserRepository users, OrderRepository orders) {
        this.users = users;
        this.orders = orders;
    }

    @GetMapping("/recharge")
    public String rechargePage(HttpSession session, Model model) {
        String u = (String) session.getAttribute("username");
        if (u == null) return "redirect:/login";
        model.addAttribute("packages", CatalogData.RECHARGE_PACKAGES);
        return "recharge";
    }

    @PostMapping("/buy_recharge")
    public String buyRecharge(@RequestParam String pkg,
                              HttpSession session,
                              RedirectAttributes flash2) {
        String username = (String) session.getAttribute("username");
        if (username == null) return "redirect:/login";
        Map<String, Object> meta = CatalogData.RECHARGE_PACKAGES.get(pkg);
        if (meta == null) {
            flash2.addFlashAttribute("error", "套餐不存在");
            return "redirect:/recharge";
        }
        Order o = new Order();
        o.setOrderId(UUID.randomUUID().toString().substring(0, 12));
        o.setUsername(username);
        o.setKind("recharge");
        o.setPkgKey(pkg);
        o.setTier("");
        o.setDurationDays(0);
        o.setPriceCny((int) meta.get("price_cny"));
        o.setPoints((int) meta.get("points"));
        o.setBonusPoints((int) meta.get("bonus_points"));
        o.setStatus("pending");
        o.setCreatedAt(LocalDateTime.now().toString());
        orders.save(o);
        return "redirect:/recharge_pay/" + o.getOrderId();
    }

    @GetMapping("/recharge_pay/{orderId}")
    public String rechargePay(@PathVariable String orderId,
                              HttpSession session,
                              Model model,
                              RedirectAttributes flash2) {
        String username = (String) session.getAttribute("username");
        if (username == null) return "redirect:/login";
        Order o = orders.findById(orderId).orElse(null);
        if (o == null || !o.getUsername().equals(username)) {
            flash2.addFlashAttribute("error", "订单不存在");
            return "redirect:/recharge";
        }
        model.addAttribute("order", o);
        model.addAttribute("pkgLabel", CatalogData.RECHARGE_PACKAGES.getOrDefault(o.getPkgKey(), Map.of()).get("label"));
        return "recharge_pay";
    }

    @GetMapping("/recharge_pay_confirm/{orderId}")
    public String rechargeConfirm(@PathVariable String orderId,
                                  HttpSession session,
                                  RedirectAttributes flash2) {
        String username = (String) session.getAttribute("username");
        if (username == null) return "redirect:/login";
        Order o = orders.findById(orderId).orElse(null);
        if (o == null || !o.getUsername().equals(username)) {
            flash2.addFlashAttribute("error", "订单不存在");
            return "redirect:/recharge";
        }
        if (!"pending".equals(o.getStatus())) {
            flash2.addFlashAttribute("error", "订单状态异常");
            return "redirect:/recharge";
        }
        User u = users.findById(username).orElse(null);
        if (u == null) return "redirect:/login";
        int total = o.getPoints() + o.getBonusPoints();
        u.setPoints(u.getPoints() + total);
        RechargeRecord rr = new RechargeRecord();
        rr.setOrderId(o.getOrderId());
        rr.setPkgKey(o.getPkgKey());
        rr.setPriceCny(o.getPriceCny());
        rr.setPoints(o.getPoints());
        rr.setBonus(o.getBonusPoints());
        rr.setPaidAt(LocalDateTime.now().toString());
        if (u.getRechargeHistory() == null) u.setRechargeHistory(new java.util.ArrayList<>());
        u.getRechargeHistory().add(rr);
        users.save(u);
        o.setStatus("paid");
        o.setPaidAt(LocalDateTime.now().toString());
        orders.save(o);
        flash2.addFlashAttribute("success", "充值成功 +" + total + " 积分");
        return "redirect:/recharge";
    }
}