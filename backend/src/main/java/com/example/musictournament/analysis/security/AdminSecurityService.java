package com.example.musictournament.analysis.security;

import com.example.musictournament.model.AdminAccount;
import com.example.musictournament.repo.AdminAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AdminSecurityService {

    private final AdminAccountRepository adminAccountRepository;
    private final String adminToken;

    public AdminSecurityService(
            AdminAccountRepository adminAccountRepository,
            @Value("${app.admin-token:admin-demo-2026}") String adminToken
    ) {
        this.adminAccountRepository = adminAccountRepository;
        this.adminToken = adminToken != null ? adminToken.trim() : "admin-demo-2026";
    }

    public enum AuthResult {
        AUTHORIZED_ADMIN,
        FORBIDDEN_NOT_ADMIN,
        UNAUTHORIZED
    }

    /**
     * Authenticates an incoming request based on headers or parameters.
     * @param token X-Admin-Token or Bearer token
     * @param email X-Admin-Email
     * @param password X-Admin-Password
     * @return AuthResult status
     */
    public AuthResult verifyAdminAccess(String token, String email, String password) {
        // Clean token if Bearer prefix present
        if (token != null) {
            token = token.trim();
            if (token.toLowerCase().startsWith("bearer ")) {
                token = token.substring(7).trim();
            }
        }

        // 1. Direct admin token check
        if (token != null && !token.isBlank()) {
            if (token.equals(adminToken)) {
                return AuthResult.AUTHORIZED_ADMIN;
            }
            // Check if token represents an admin email
            Optional<AdminAccount> accOpt = adminAccountRepository.findByEmailIgnoreCase(token);
            if (accOpt.isPresent()) {
                AdminAccount acc = accOpt.get();
                if ("ADMIN".equalsIgnoreCase(acc.getRole())) {
                    return AuthResult.AUTHORIZED_ADMIN;
                } else {
                    return AuthResult.FORBIDDEN_NOT_ADMIN;
                }
            }
        }

        // 2. Email + Password check
        if (email != null && !email.isBlank()) {
            Optional<AdminAccount> accOpt = adminAccountRepository.findByEmailIgnoreCase(email.trim());
            if (accOpt.isPresent()) {
                AdminAccount acc = accOpt.get();
                if (password != null && password.trim().equals(acc.getPassword())) {
                    if ("ADMIN".equalsIgnoreCase(acc.getRole())) {
                        return AuthResult.AUTHORIZED_ADMIN;
                    } else {
                        return AuthResult.FORBIDDEN_NOT_ADMIN;
                    }
                }
                return AuthResult.UNAUTHORIZED;
            }
        }

        // Backward compatibility: password matching adminToken
        if (password != null && password.trim().equals(adminToken)) {
            return AuthResult.AUTHORIZED_ADMIN;
        }

        return AuthResult.UNAUTHORIZED;
    }
}
