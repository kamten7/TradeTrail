package com.tradetrail.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradetrail.application.integration.CommandService;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, CommandService commands,
            PlatformTransactionManager transactions, ObjectMapper json) throws Exception {
        return http
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/callbacks/**"))
                .authorizeHttpRequests(
                        auth -> auth.requestMatchers(HttpMethod.GET, "/products", "/products/*", "/session")
                                .permitAll()
                                .requestMatchers("/callbacks/**")
                                .permitAll()
                                .requestMatchers("/admin/**")
                                .hasRole("MERCHANT_ADMIN")
                                .requestMatchers("/cart/**", "/orders/**", "/after-sales/**", "/mock/checkout/**")
                                .hasRole("CUSTOMER")
                                .anyRequest()
                                .authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(401);
                    res.setContentType("application/json;charset=UTF-8");
                    json.writeValue(res.getOutputStream(),
                            ApiErrorHandler.body("UNAUTHENTICATED", "请先登录", "", List.of()));
                }).accessDeniedHandler((req, res, e) -> {
                    res.setStatus(403);
                    res.setContentType("application/json;charset=UTF-8");
                    json.writeValue(res.getOutputStream(),
                            ApiErrorHandler.body("FORBIDDEN", "无权访问或 CSRF 校验失败", "", List.of()));
                }))
                .addFilterAfter(new IdempotencyFilter(commands, transactions, json), AuthorizationFilter.class)
                .requestCache(cache -> cache.disable())
                .formLogin(login -> login.successHandler((req, res, authentication) -> {
                    res.setStatus(200);
                    res.setContentType("application/json;charset=UTF-8");
                    json.writeValue(res.getOutputStream(), Map.of("user", authentication.getName(), "roles",
                            authentication.getAuthorities().stream().map(Object::toString).toList()));
                }).failureHandler((req, res, error) -> {
                    res.setStatus(401);
                    res.setContentType("application/json;charset=UTF-8");
                    json.writeValue(res.getOutputStream(),
                            ApiErrorHandler.body("LOGIN_FAILED", "账号或密码不正确", "", List.of()));
                }))
                .logout(Customizer.withDefaults())
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService users(PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername("customer").password(encoder.encode("customer123")).roles("CUSTOMER").build(),
                User.withUsername("merchant").password(encoder.encode("merchant123")).roles("MERCHANT_ADMIN").build());
    }
}
