package com.starstack.controller;

import com.starstack.model.MouseEffectConfig;
import com.starstack.model.User;
import com.starstack.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/** Settings page: avatar upload, mouse config, password change. */
@Controller
public class SettingsController {

    private final UserRepository users;
    private final Path avatarDir = Paths.get("data", "avatars");

    @Autowired
    public SettingsController(UserRepository users) throws IOException {
        this.users = users;
        Files.createDirectories(avatarDir);
    }

    @GetMapping("/settings")
    public String settings(HttpSession session, Model model) {
        String username = (String) session.getAttribute("username");
        if (username == null) return "redirect:/login";
        return "settings";
    }

    @PostMapping("/settings/avatar")
    public String avatar(@RequestParam("file") MultipartFile file,
                         HttpSession session,
                         RedirectAttributes flash2) throws IOException {
        String username = (String) session.getAttribute("username");
        if (username == null) return "redirect:/login";
        if (file.isEmpty()) {
            flash2.addFlashAttribute("error", "文件为空");
            return "redirect:/settings";
        }
        String ext = "";
        String orig = file.getOriginalFilename();
        if (orig != null && orig.contains(".")) ext = orig.substring(orig.lastIndexOf('.'));
        String fn = username + "_" + UUID.randomUUID() + ext;
        Path target = avatarDir.resolve(fn);
        Files.write(target, file.getBytes());
        User u = users.findById(username).orElse(null);
        if (u != null) {
            u.setAvatar("/avatars/" + fn);
            users.save(u);
        }
        flash2.addFlashAttribute("success", "头像已更新");
        return "redirect:/settings";
    }

    @GetMapping("/get_mouse_config")
    @ResponseBody
    public MouseEffectConfig getMouseConfig(HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username == null) return new MouseEffectConfig();
        return users.findById(username).map(User::getMouseConfig).orElse(new MouseEffectConfig());
    }

    @PostMapping("/save_mouse_config")
    @ResponseBody
    public String saveMouseConfig(@RequestParam(required = false) Boolean enabled,
                                  @RequestParam(required = false) String color_mode,
                                  @RequestParam(required = false) String shape,
                                  HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username == null) return "{\"ok\":false}";
        User u = users.findById(username).orElse(null);
        if (u == null) return "{\"ok\":false}";
        MouseEffectConfig c = u.getMouseConfig();
        if (c == null) c = new MouseEffectConfig();
        if (enabled != null) c.setEnabled(enabled);
        if (color_mode != null) c.setColorMode(color_mode);
        if (shape != null) c.setShape(shape);
        u.setMouseConfig(c);
        users.save(u);
        return "{\"ok\":true}";
    }
}