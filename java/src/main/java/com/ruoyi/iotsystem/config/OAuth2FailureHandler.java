package com.ruoyi.iotsystem.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;

@Component
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2FailureHandler.class);

    @Value("${app.oauth2.redirect-uri}")
    private String redirectUri;

    // OAuth2登录失败处理：记录日志、重定向到前端登录页
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        logger.error("OAuth2 登录失败: {}", exception.getMessage(), exception);
        String msg = URLEncoder.encode(exception.getMessage(), "UTF-8");
        response.sendRedirect(redirectUri + "/#/login?oauth_error=" + msg);
    }
}
