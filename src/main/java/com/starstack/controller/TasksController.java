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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Tasks page — list fixed + daily tasks, claim rewards. */
@Controller
public class TasksController {

    private final UserRepository users;
    private final TaskService tasks;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    public TasksController(UserRepository users, TaskService tasks) {
        this.users = users;
        this.tasks = tasks;
    }

    @GetMapping("/tasks")
    public String tasks(Model model, HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username == null) return "redirect:/login";
        User u = users.findById(username).orElse(null);
        if (u == null) return "redirect:/login";
        model.addAttribute("fixedTasks", CatalogData.FIXED_TASKS);
        model.addAttribute("dailyTasks", CatalogData.DAILY_TASK_POOL);
        model.addAttribute("user", u);
        return "tasks";
    }

    @PostMapping("/tasks/claim")
    public String claim(@RequestParam String taskId,
                        @RequestParam String kind,
                        HttpSession session,
                        RedirectAttributes flash2) {
        String username = (String) session.getAttribute("username");
        if (username == null) return "redirect:/login";
        User u = users.findById(username).orElse(null);
        if (u == null) return "redirect:/login";
        boolean fired;
        int reward = 0;
        if ("fixed".equals(kind)) {
            reward = fixedReward(taskId);
            fired = tasks.fireFixed(u, taskId, reward);
        } else {
            reward = dailyReward(taskId);
            fired = tasks.fireDaily(u, taskId, reward);
        }
        if (fired) users.save(u);
        flash2.addFlashAttribute("success", fired ? "已领取 +" + reward : "今日已领取");
        return "redirect:/tasks";
    }

    private static int fixedReward(String id) {
        return switch (id) {
            case "post_message" -> 50;
            case "buy_item"     -> 30;
            case "use_item"     -> 30;
            case "spend_master" -> 200;
            default             -> 0;
        };
    }

    private static int dailyReward(String id) {
        return switch (id) {
            case "daily_visit" -> 5;
            case "daily_login" -> 10;
            case "daily_buy"   -> 15;
            case "daily_use"   -> 15;
            case "daily_post"  -> 10;
            case "daily_vip"   -> 5;
            default            -> 0;
        };
    }
}