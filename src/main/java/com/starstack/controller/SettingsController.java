package com.starstack.controller;

import com.starstack.model.MouseEffectConfig;
import com.starstack.model.User;
import com.starstack.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import java.util.regex.Pattern;

/** Settings page: avatar upload, mouse config, password change, phone. */
@Controller
public class SettingsController {

    private static final Pattern PHONE_RE = Pattern.compile("^1[3-9]\\d{9}$");

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Path avatarDir = Paths.get("data", "avatars");

    @Autowired
    public SettingsController(UserRepository users, PasswordEncoder encoder) throws IOException {
        this.users = users;
        this.encoder = encoder;
        Files.createDirectories(avatarDir);
    }

    @GetMapping("/settings")
    public String settings(HttpSession session, Model model) {
        String username = (String) session.getAttribute("username");
        if (username == null) return "redirect:/login";
        return "settings";
    }

    @PostMapping("/settings")
    public String updateProfile(@RequestParam(required = false) String old_password,
                                @RequestParam(required = false) String new_password,
                                @RequestParam(required = false) String confirm_password,
                                @RequestParam(required = false) String phone,
                                HttpSession session,
                                RedirectAttributes flash2) {
        String username = (String) session.getAttribute("username");
        if (username == null) return "redirect:/login";
        User u = users.findById(username).orElse(null);
        if (u == null) {
            flash2.addFlashAttribute("error", "用户不存在");
            return "redirect:/settings";
        }

        // Password change branch
        if (old_password != null && new_password != null && confirm_password != null
            && !(old_password.isEmpty() && new_password.isEmpty() && confirm_password.isEmpty())) {
            if (old_password.isEmpty() || new_password.isEmpty() || confirm_password.isEmpty()) {
                flash2.addFlashAttribute("error", "所有密码字段都必须填写");
                return "redirect:/settings";
            }
            if (!encoder.matches(old_password, u.getPasswordHash())) {
                flash2.addFlashAttribute("error", "原密码错误");
                return "redirect:/settings";
            }
            if (!new_password.equals(confirm_password)) {
                flash2.addFlashAttribute("error", "两次新密码不一致");
                return "redirect:/settings";
            }
            if (new_password.length() < 8 || new_password.length() > 16) {
                flash2.addFlashAttribute("error", "新密码长度必须在 8~16 位之间");
                return "redirect:/settings";
            }
            u.setPasswordHash(encoder.encode(new_password));
            u.setPasswordFormat("bcrypt");
            users.save(u);
            flash2.addFlashAttribute("success", "密码更新成功");
            return "redirect:/settings";
        }

        // Phone update branch
        if (phone != null && !phone.isEmpty()) {
            if (!PHONE_RE.matcher(phone).matches()) {
                flash2.addFlashAttribute("error", "手机号格式错误（11 位，1 开头）");
                return "redirect:/settings";
            }
            u.setPhone(phone);
            users.save(u);
            flash2.addFlashAttribute("success", "手机号已保存");
            return "redirect:/settings";
        }

        flash2.addFlashAttribute("error", "未提供需要更新的字段");
        return "redirect:/settings";
    }

    @PostMapping("/settings/avatar")
    public String avatar(@RequestParam(value = "file", required = false) MultipartFile file,
                         @RequestParam(value = "action", required = false) String action,
                         HttpSession session,
                         RedirectAttributes flash2) throws IOException {
        String username = (String) session.getAttribute("username");
        if (username == null) return "redirect:/login";
        User u = users.findById(username).orElse(null);
        if (u == null) {
            flash2.addFlashAttribute("error", "用户不存在");
            return "redirect:/settings";
        }

        // Reset to default
        if ("reset".equals(action)) {
            u.setAvatar("default:none");
            users.save(u);
            flash2.addFlashAttribute("success", "已恢复默认头像");
            return "redirect:/settings";
        }

        if (file == null || file.isEmpty()) {
            flash2.addFlashAttribute("error", "文件为空");
            return "redirect:/settings";
        }
        String ext = "";
        String orig = file.getOriginalFilename();
        if (orig != null && orig.contains(".")) ext = orig.substring(orig.lastIndexOf('.'));
        String fn = username + "_" + UUID.randomUUID() + ext;
        Path target = avatarDir.resolve(fn);
        Files.write(target, file.getBytes());
        u.setAvatar("/avatars/" + fn);
        users.save(u);
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