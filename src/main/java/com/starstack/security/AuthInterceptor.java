package com.starstack.security;

import com.starstack.model.User;
import com.starstack.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Hand-rolled auth interceptor. Spring's standard UserDetailsService flow
 * can't write back to the user record on login (we rehash scrypt → BCrypt),
 * so we manage a session["username"] ourselves and check it here.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final UserRepository users;

    @Autowired
    public AuthInterceptor(UserRepository users) {
        this.users = users;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        String path = req.getRequestURI();
        // Public endpoints
        if (isPublic(path)) return true;

        HttpSession session = req.getSession(false);
        String u = session == null ? null : (String) session.getAttribute("username");
        if (u == null) {
            String next = req.getRequestURI();
            res.sendRedirect(req.getContextPath() + "/login?next=" + java.net.URLEncoder.encode(next, "UTF-8"));
            return false;
        }
        User user = users.findById(u).orElse(null);
        if (user == null) {
            session.invalidate();
            res.sendRedirect(req.getContextPath() + "/login");
            return false;
        }
        return true;
    }

    private boolean isPublic(String path) {
        if (path.equals("/") || path.equals("/message_board")
            || path.startsWith("/login") || path.startsWith("/register")
            || path.startsWith("/static/") || path.startsWith("/css/")
            || path.startsWith("/js/") || path.startsWith("/images/")
            || path.startsWith("/avatars/") || path.startsWith("/api/vip_status")
            || path.equals("/favicon.ico")) return true;
        return false;
    }
}