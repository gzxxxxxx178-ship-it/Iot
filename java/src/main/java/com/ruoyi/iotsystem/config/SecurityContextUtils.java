package com.ruoyi.iotsystem.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 提供当前请求认证用户的统一读取入口，避免控制器自行信任客户端传入的用户名。
 */
public final class SecurityContextUtils {

    private SecurityContextUtils() {
    }

    // 获取当前已认证用户的用户名
    public static String requireUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new SecurityException("未登录或登录状态已失效");
        }
        return authentication.getName();
    }

    // 在已由安全过滤器保护的控制器测试或内部调用中读取可选用户名
    public static String currentUsernameOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        return authentication.getName();
    }
}
