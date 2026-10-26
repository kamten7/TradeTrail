package com.tradetrail.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionController {
    @GetMapping("/session")
    public Map<String, Object> session(Principal principal, CsrfToken csrfToken) {
        return Map.of("user", principal == null ? "" : principal.getName(), "csrfToken", csrfToken.getToken(),
                "roles", principal instanceof Authentication authentication ? authentication.getAuthorities().stream().map(Object::toString).toList() : java.util.List.of());
    }

    @PostMapping("/session/logout")
    public Map<String, String> logout(Authentication authentication, HttpServletRequest request,
                                      HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return Map.of("result", "signed_out");
    }
}
