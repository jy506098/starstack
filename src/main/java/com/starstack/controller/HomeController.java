package com.starstack.controller;

import com.starstack.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/** Renders the home page (board.html) with the latest messages. */
@Controller
public class HomeController {

    private final MessageRepository messages;

    @Autowired
    public HomeController(MessageRepository messages) {
        this.messages = messages;
    }

    @GetMapping("/")
    public String home(Model model) {
        List<?> latest = messages.findAllDesc();
        model.addAttribute("messages", latest.size() > 10 ? latest.subList(0, 10) : latest);
        return "board";
    }

    @GetMapping("/message_board")
    public String board(Model model) {
        model.addAttribute("messages", messages.findAllDesc());
        return "message_board";
    }
}