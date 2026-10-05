package com.starstack.controller;

import com.starstack.model.Message;
import com.starstack.model.User;
import com.starstack.repository.MessageRepository;
import com.starstack.repository.UserRepository;
import com.starstack.service.TaskService;
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
import java.util.List;
import java.util.Optional;

/** Board / post / delete / content / video_player routes. */
@Controller
public class BoardController {

    private final MessageRepository messages;
    private final UserRepository users;
    private final TaskService tasks;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    public BoardController(MessageRepository messages, UserRepository users, TaskService tasks) {
        this.messages = messages;
        this.users = users;
        this.tasks = tasks;
    }

    @GetMapping("/post")
    public String postForm() { return "board"; }

    @PostMapping("/post")
    public String doPost(@RequestParam String msg,
                         HttpSession session,
                         RedirectAttributes flash2) {
        String username = (String) session.getAttribute("username");
        if (username == null) return "redirect:/login";
        if (msg == null || msg.trim().isEmpty()) {
            flash2.addFlashAttribute("error", "留言不能为空");
            return "redirect:/";
        }
        messages.save(new Message(username, msg.trim()));
        // Fire daily_post + post_message tasks
        User u = users.findById(username).orElse(null);
        if (u != null) {
            tasks.fireDaily(u, "daily_post", 10);
            tasks.fireFixed(u, "post_message", 50);
            // reset post_master last_post_date
            u.setLastPostDate(LocalDateTime.now().format(DAY));
            users.save(u);
        }
        flash2.addFlashAttribute("success", "留言成功 +50 积分");
        return "redirect:/";
    }

    @GetMapping("/delete/{idx}")
    public String doDelete(@PathVariable Long idx,
                           HttpSession session,
                           RedirectAttributes flash2) {
        String username = (String) session.getAttribute("username");
        if (username == null) return "redirect:/login";
        Optional<Message> opt = messages.findById(idx);
        if (opt.isEmpty()) {
            flash2.addFlashAttribute("error", "留言不存在");
            return "redirect:/message_board";
        }
        Message m = opt.get();
        User u = users.findById(username).orElse(null);
        boolean isAdmin = u != null && u.isAdmin();
        boolean isOwn = username.equals(m.getName());
        if (!isAdmin && !isOwn) {
            flash2.addFlashAttribute("error", "没有权限删除该留言");
            return "redirect:/message_board";
        }
        messages.deleteById(idx);
        flash2.addFlashAttribute("success", "留言已删除");
        return "redirect:/message_board";
    }

    @GetMapping("/content/{itemName}")
    public String content(@PathVariable String itemName,
                          HttpSession session,
                          Model model,
                          RedirectAttributes flash2) {
        String username = (String) session.getAttribute("username");
        if (username == null) return "redirect:/login";
        User u = users.findById(username).orElse(null);
        if (u == null) return "redirect:/login";
        if (!u.hasUnlocked(itemName)) {
            flash2.addFlashAttribute("error", "尚未解锁 " + itemName);
            return "redirect:/store";
        }
        // Dispatch the template by item name
        return templateFor(itemName);
    }

    private String templateFor(String itemName) {
        switch (itemName) {
            case "编程秘籍":       return "programming_secrets";
            case "C++ 入门":       return "cpp_tutorial";
            case "node.js 入门":   return "nodejs_tutorial";
            case "前端三剑客 入门": return "frontend_tutorial";
            case "Python 入门":    return "python_tutorial";
            case "Python后端 入门": return "backend_getting_started";
            case "降噪耳机":       return "headphones";
            case "影视播放器":      return "video_player";
            default:               return "board";
        }
    }

    @GetMapping("/video_player")
    public String videoPlayer(Model model) {
        model.addAttribute("movieUrl", "/static/video/sample.mp4");
        return "video_player";
    }
}