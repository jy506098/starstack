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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Inventory page + use-item effect activation. */
@Controller
public class InventoryController {

    private final UserRepository users;
    private final TaskService tasks;

    @Autowired
    public InventoryController(UserRepository users, TaskService tasks) {
        this.users = users;
        this.tasks = tasks;
    }

    @GetMapping("/inventory")
    public String inventory(HttpSession session, Model model) {
        String username = (String) session.getAttribute("username");
        if (username == null) return "redirect:/login";
        User u = users.findById(username).orElse(null);
        if (u == null) return "redirect:/login";

        // Show only items the user actually owns (count > 0), merged with their metadata
        Map<String, Integer> inv = u.getInventory();
        if (inv == null) inv = new LinkedHashMap<>();
        Map<String, Map<String, Object>> owned = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : inv.entrySet()) {
            if (e.getValue() <= 0) continue;
            Map<String, Object> meta = CatalogData.ITEM_DATA.get(e.getKey());
            if (meta == null) continue;
            Map<String, Object> row = new LinkedHashMap<>(meta);
            row.put("count", e.getValue());
            owned.put(e.getKey(), row);
        }
        model.addAttribute("items", owned);
        return "inventory";
    }

    @PostMapping("/use_item")
    public String useItem(@RequestParam String item,
                          HttpSession session,
                          RedirectAttributes flash2) {
        String username = (String) session.getAttribute("username");
        if (username == null) return "redirect:/login";
        User u = users.findById(username).orElse(null);
        if (u == null) return "redirect:/login";
        if (!u.hasUnlocked(item) || u.inventoryCount(item) <= 0) {
            flash2.addFlashAttribute("error", "未拥有 " + item);
            return "redirect:/inventory";
        }
        Map<String, Object> meta = CatalogData.ITEM_DATA.get(item);
        String type = meta == null ? "" : (String) meta.get("type");
        String redirect = "/inventory";

        if ("accessory".equals(type) || "skin".equals(type)) {
            if (u.getEffects() == null) u.setEffects(new LinkedHashMap<>());
            u.getEffects().put(item, true);
        } else if ("tutorial".equals(type) || "media".equals(type)) {
            // Direct to content page
            redirect = "/content/" + item;
        } else if ("game_unlock".equals(type)) {
            flash2.addFlashAttribute("success", "已解锁游戏中心");
        }
        // Effect: decrement count by 1 (only for consumables)
        if (!"game_unlock".equals(type)) {
            Map<String, Integer> inv = u.getInventory();
            int n = inv.getOrDefault(item, 0);
            if (n <= 1) inv.remove(item);
            else inv.put(item, n - 1);
        }
        tasks.fireFixed(u, "use_item", 30);
        tasks.fireDaily(u, "daily_use", 15);
        users.save(u);
        return "redirect:" + redirect;
    }
}