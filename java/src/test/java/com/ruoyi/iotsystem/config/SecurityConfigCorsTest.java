package com.ruoyi.iotsystem.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigCorsTest {
    // 验证跨域认证仅接受必需请求头，且不向浏览器暴露全部响应头
    @Test
    void corsConfiguration_认证请求_仅允许最小请求头集合() {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins", "https://app.example.com");

        UrlBasedCorsConfigurationSource source = (UrlBasedCorsConfigurationSource) config.corsConfigurationSource();
        CorsConfiguration cors = source.getCorsConfiguration(new org.springframework.mock.web.MockHttpServletRequest());

        assertTrue(cors.getAllowedHeaders().contains("Authorization"));
        assertTrue(cors.getAllowedHeaders().contains("X-XSRF-TOKEN"));
        assertFalse(cors.getAllowedHeaders().contains("*"));
        assertTrue(cors.getExposedHeaders().isEmpty());
    }
}
