package com.starstack.controller;

import com.starstack.model.User;
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

/** Game center + buy game + play_game dispatch. */
@Controller
public class GameController {

    private final UserRepository users;

    @Autowired
    public GameController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/game_center")
    public String gameCenter(HttpSession session, Model model, RedirectAttributes flash2) {
        String username = (String) session.getAttribute("username");
        if (username == null) return "redirect:/login";
        User u = users.findById(username).orElse(null);
        if (u == null) return "redirect:/login";
        if (!u.hasUnlocked("游戏手柄")) {
            flash2.addFlashAttribute("error", "需要先购买「游戏手柄」");
            return "redirect:/store";
        }
        model.addAttribute("games", CatalogData.GAME_LIST);
        model.addAttribute("prices", CatalogData.GAME_PRICES);
        return "game_center";
    }

    @PostMapping("/buy_game")
    public String buyGame(@RequestParam String game,
                          HttpSession session,
                          RedirectAttributes flash2) {
        String username = (String) session.getAttribute("username");
        if (username == null) return "redirect:/login";
        User u = users.findById(username).orElse(null);
        if (u == null) return "redirect:/login";
        if (!u.hasUnlocked("游戏手柄")) {
            flash2.addFlashAttribute("error", "需要先购买「游戏手柄」");
            return "redirect:/store";
        }
        var price = CatalogData.GAME_PRICES.get(game);
        if (price == null) {
            flash2.addFlashAttribute("error", "游戏不存在");
            return "redirect:/game_center";
        }
        if (u.hasUnlocked(game)) {
            flash2.addFlashAttribute("error", "已拥有 " + game);
            return "redirect:/game_center";
        }
        int p = (int) price.get("price");
        boolean isVip = !u.getVipTier().isEmpty();
        if (isVip) p = (int) Math.round(p * 0.9);
        if (u.getPoints() < p) {
            flash2.addFlashAttribute("error", "积分不足");
            return "redirect:/game_center";
        }
        u.setPoints(u.getPoints() - p);
        u.setTotalSpent(u.getTotalSpent() + p);
        u.getUnlockedContent().add(game);
        users.save(u);
        flash2.addFlashAttribute("success", "已购买 " + game);
        return "redirect:/game_center";
    }

    @GetMapping("/play_game/{gameName}")
    public String playGame(@PathVariable String gameName,
                           HttpSession session,
                           Model model,
                           RedirectAttributes flash2) {
        String username = (String) session.getAttribute("username");
        if (username == null) return "redirect:/login";
        User u = users.findById(username).orElse(null);
        if (u == null) return "redirect:/login";
        if (!u.hasUnlocked(gameName)) {
            flash2.addFlashAttribute("error", "尚未拥有 " + gameName);
            return "redirect:/game_center";
        }
        model.addAttribute("gameName", gameName);
        model.addAttribute("gameJsFile", CatalogData.GAME_NAME_TO_JSFILE.getOrDefault(gameName, ""));
        return "play_game";
    }
}