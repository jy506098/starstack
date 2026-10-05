package com.starstack.config;

import com.starstack.security.AuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/login", "/register", "/logout",
                        "/", "/index",
                        "/css/**", "/js/**", "/images/**", "/avatars/**", "/webp/**",
                        "/error"
                );
    }

    /**
     * Force UTF-8 request/response encoding — Tomcat's default is ISO-8859-1
     * which mangles CJK form data.
     */
    @Bean
    public FilterRegistrationBean<CharacterEncodingFilter> starstackEncodingFilter() {
        CharacterEncodingFilter f = new CharacterEncodingFilter(StandardCharsets.UTF_8.name(), true, true);
        FilterRegistrationBean<CharacterEncodingFilter> reg = new FilterRegistrationBean<>(f);
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return reg;
    }
}