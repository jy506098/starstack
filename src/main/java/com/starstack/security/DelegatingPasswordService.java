package com.starstack.security;

import com.starstack.model.User;
import com.starstack.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Routes password verification between legacy werkzeug-scrypt (read-only) and
 * new BCrypt (write). On first successful scrypt login, the user's hash is
 * transparently rewritten to BCrypt.
 */
@Service
public class DelegatingPasswordService {

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(10);
    private final WerkzeugScryptPasswordEncoder scrypt = new WerkzeugScryptPasswordEncoder();

    @Autowired
    private UserRepository users;

    public boolean verify(String username, String raw, User user) {
        if (user == null || raw == null) return false;
        String stored = user.getPasswordHash();
        if (stored == null) return false;
        boolean ok;
        if (stored.startsWith("scrypt:")) {
            ok = scrypt.matches(raw, stored);
            if (ok) {
                user.setPasswordHash(bcrypt.encode(raw));
                user.setPasswordFormat("bcrypt");
                users.save(user);
            }
        } else {
            ok = bcrypt.matches(raw, stored);
        }
        return ok;
    }

    public String hashForNewUser(String raw) {
        return bcrypt.encode(raw);
    }
}