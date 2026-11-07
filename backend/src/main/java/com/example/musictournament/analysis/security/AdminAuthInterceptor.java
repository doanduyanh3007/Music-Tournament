package com.example.musictournament.analysis.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final AdminSecurityService securityService;

    public AdminAuthInterceptor(AdminSecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Allow CORS pre-flight requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("X-Admin-Token");
        if (token == null || token.isBlank()) {
            token = request.getHeader("Authorization");
        }
        if (token == null || token.isBlank()) {
            token = request.getParameter("adminToken");
        }

        String email = request.getHeader("X-Admin-Email");
        String password = request.getHeader("X-Admin-Password");

        AdminSecurityService.AuthResult result = securityService.verifyAdminAccess(token, email, password);

        if (result == AdminSecurityService.AuthResult.AUTHORIZED_ADMIN) {
            return true;
        }

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        if (result == AdminSecurityService.AuthResult.FORBIDDEN_NOT_ADMIN) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Tài khoản không có quyền ADMIN để truy cập Song Analysis.\"}");
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Yêu cầu đăng nhập tài khoản Quản trị viên (ADMIN) để sử dụng chức năng này.\"}");
        }

        return false;
    }
}
