package com.payguard.engine.config.interceptor;

import com.payguard.engine.entity.MerchantApp;
import com.payguard.engine.repository.MerchantAppRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ApiKeyInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyInterceptor.class);
    private final MerchantAppRepository merchantAppRepository;

    public ApiKeyInterceptor(MerchantAppRepository merchantAppRepository) {
        this.merchantAppRepository = merchantAppRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Cho qua request OPTIONS (CORS preflight)
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String apiKey = request.getHeader("X-API-KEY");
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("==> [Security] Missing X-API-KEY header for URI: {}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        MerchantApp app = merchantAppRepository.findByApiKeyAndActiveTrue(apiKey).orElse(null);
        if (app == null) {
            log.warn("==> [Security] Invalid or inactive API Key: {}", apiKey);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        // Nhét App ID đã xác thực vào Request để Controller rút ra dùng
        request.setAttribute("X-APP-ID", app.getAppId());
        log.info("==> [Security] Authenticated merchant app: {}", app.getAppId());
        return true;
    }
}
