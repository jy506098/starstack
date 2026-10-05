package com.starstack.controller;

import com.starstack.model.User;
import com.starstack.repository.UserRepository;
import com.starstack.service.VipService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/** Public API endpoints. */
@RestController
public class ApiController {

    @Autowired
    private UserRepository users;

    @Autowired
    private VipService vipService;

    @GetMapping("/api/vip_status")
    public Map<String, Object> vipStatus(HttpSession session) {
        Map<String, Object> out = new HashMap<>();
        String username = (String) session.getAttribute("username");
        if (username == null) {
            out.put("isVip", false);
            out.put("tier", "");
            out.put("daysLeft", 0);
            return out;
        }
        User u = users.findById(username).orElse(null);
        if (u == null) {
            out.put("isVip", false);
            return out;
        }
        VipService.VipStatus s = vipService.status(u);
        out.put("isVip", s.isVip());
        out.put("tier", s.tier());
        out.put("daysLeft", s.daysLeft());
        return out;
    }
}