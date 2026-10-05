package com.starstack.controller;

import com.starstack.model.User;
import com.starstack.repository.UserRepository;
import com.starstack.service.CatalogData;
import com.starstack.service.TaskService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/** Storefront + buy action. */
@Controller
public class StoreController {

    private final UserRepository users;
    private final TaskService tasks;
    private final CatalogData catalog;

    @Autowired
    public StoreController(UserRepository users, TaskService tasks, CatalogData catalog) {
        this.users = users;
        this.tasks = tasks;
        this.catalog = catalog;
    }

    @GetMapping("/store")
    public String store(Model model) {
        model.addAttribute("items", CatalogData.ITEM_DATA);
        return "store";
    }

    @PostMapping("/buy")
    public String doBuy(@RequestParam String item,
                        HttpSession session,
                        RedirectAttributes flash2) {
        String username = (String) session.getAttribute("username");
        if (username == null) return "redirect:/login";
        Map<String, Object> meta = CatalogData.ITEM_DATA.get(item);
        if (meta == null) {
            flash2.addFlashAttribute("error", "商品不存在");
            return "redirect:/store";
        }
        User u = users.findById(username).orElse(null);
        if (u == null) return "redirect:/login";
        if (u.hasUnlocked(item)) {
            flash2.addFlashAttribute("error", "已拥有 " + item);
            return "redirect:/store";
        }
        int price = (int) meta.get("price");
        boolean isVip = !u.getVipTier().isEmpty();
        if (isVip && "tutorial".equals(meta.get("type"))) {
            // VIP 9折 on tutorials (free for SSVIP); SSVIP gets tutorials free
            if ("SSVIP".equals(u.getVipTier())) {
                price = 0;
            } else {
                price = (int) Math.round(price * 0.9);
            }
        }
        if (u.getPoints() < price) {
            flash2.addFlashAttribute("error", "积分不足");
            return "redirect:/store";
        }
        u.setPoints(u.getPoints() - price);
        u.setTotalSpent(u.getTotalSpent() + price);
        u.getUnlockedContent().add(item);
        if (u.getInventory() == null) u.setInventory(new LinkedHashMap<>());
        u.getInventory().merge(item, 1, Integer::sum);
        tasks.fireFixed(u, "buy_item", 30);
        tasks.fireDaily(u, "daily_buy", 15);
        if (u.getTotalSpent() >= 500) tasks.fireFixed(u, "spend_master", 200);
        users.save(u);
        flash2.addFlashAttribute("success", "已购买 " + item + (isVip ? "（VIP 价）" : ""));
        return "redirect:/inventory";
    }
}