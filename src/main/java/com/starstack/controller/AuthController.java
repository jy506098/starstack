package com.starstack.controller;

import com.starstack.model.User;
import com.starstack.repository.UserRepository;
import com.starstack.security.DelegatingPasswordService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;

/**
 * Login / register / logout — replaces the legacy Python auth routes.
 *
 * Login is custom: we need to mutate the user record on first successful
 * scrypt verify (rehash → BCrypt). DelegatingPasswordService handles that.
 */
@Controller
public class AuthController {

    private final UserRepository users;
    private final DelegatingPasswordService passwords;

    @Autowired
    public AuthController(UserRepository users, DelegatingPasswordService passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "next", required = false) String next, Model model) {
        model.addAttribute("next", next == null ? "/" : next);
        return "login";
    }

    @PostMapping("/login")
    public String doLogin(@RequestParam String username,
                          @RequestParam String password,
                          @RequestParam(value = "next", required = false) String next,
                          HttpSession session,
                          RedirectAttributes flash2) {
        User user = users.findById(username).orElse(null);
        if (user == null || !passwords.verify(username, password, user)) {
            flash2.addFlashAttribute("error", "用户名或密码错误");
            return "redirect:/login";
        }
        session.setAttribute("username", username);
        String target = (next == null || next.isEmpty() || next.contains("/login")) ? "/" : next;
        return "redirect:" + target;
    }

    @GetMapping("/register")
    public String registerPage() { return "register"; }

    @PostMapping("/register")
    public String doRegister(@RequestParam String username,
                             @RequestParam String password,
                             @RequestParam(value = "phone", required = false) String phone,
                             RedirectAttributes flash2) {
        if (username == null || username.isEmpty() || password == null || password.length() < 8 || password.length() > 16) {
            flash2.addFlashAttribute("error", "用户名不能为空；密码 8~16 位");
            return "redirect:/register";
        }
        if (users.existsById(username)) {
            flash2.addFlashAttribute("error", "用户名已被使用");
            return "redirect:/register";
        }
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(passwords.hashForNewUser(password));
        u.setPasswordFormat("bcrypt");
        u.setPhone(phone == null ? "" : phone);
        u.setPoints(100);
        u.setAvatar("default:none");
        u.setInventory(new LinkedHashMap<>());
        u.setUnlockedContent(new java.util.ArrayList<>());
        u.setEffects(new LinkedHashMap<>());
        u.setMouseConfig(new com.starstack.model.MouseEffectConfig());
        u.setVipTier("");
        u.setVipExpiresAt("");
        u.setLastVipBonusDate("");
        u.setVipPendingOrderId("");
        u.setVipPurchaseHistory(new java.util.ArrayList<>());
        u.setRechargeHistory(new java.util.ArrayList<>());
        u.setFixedTasks(new LinkedHashMap<>());
        u.setDailyTasks(new LinkedHashMap<>());
        u.setCreatedAt(LocalDateTime.now().toString());
        u.setUpdatedAt(LocalDateTime.now().toString());
        users.save(u);
        flash2.addFlashAttribute("success", "注册成功，请登录");
        return "redirect:/login";
    }

    @PostMapping("/logout")
    public String doLogout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }

    @GetMapping("/logout")
    public String logoutGet(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }
}