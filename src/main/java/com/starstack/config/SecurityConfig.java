package com.starstack.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * StarStack security config.
 *
 * We do not use Spring Security's authentication pipeline (form login / Basic
 * / UserDetailsService) because we need to mutate the User record (rehash
 * legacy scrypt → BCrypt) on successful login. Instead we use a hand-rolled
 * session/AuthInterceptor approach in {@code AuthInterceptor}.
 *
 * Spring Security is kept on the classpath only for {@code BCryptPasswordEncoder}
 * and the CSRF helper.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .securityContext(sc -> sc.requireExplicitSave(false))
            // Disable form login / basic — our AuthController handles login.
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .logout(l -> l.disable())
            // CSRF off (single-user local app; SameSite=Lax cookie is the safety net)
            .csrf(c -> c.disable())
            // Authorization: open by default; AuthInterceptor enforces login for protected routes.
            .authorizeHttpRequests(a -> a.anyRequest().permitAll());

        return http.build();
    }
}