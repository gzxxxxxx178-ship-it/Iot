package com.ruoyi.iotsystem.config;

import com.ruoyi.iotsystem.entity.UserEntity;
import com.ruoyi.iotsystem.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;

@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Value("${app.oauth2.redirect-uri}")
    private String redirectUri;

    // OAuth2登录成功处理：生成JWT，重定向到前端回调页
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        // 从我们注入的 customAttributes 里拿数据库 username
        String username = (String) oAuth2User.getAttributes().get("db_username");
        if (username == null) {
            // fallback：直接查数据库
            String providerId = (String) oAuth2User.getAttributes().get("sub");
            UserEntity user = userRepository.findByProviderAndProviderId("GOOGLE", providerId);
            username = user != null ? user.getUsername() : "unknown";
        }

        String token = jwtUtil.generateToken(username);

        String redirectUrl = redirectUri + "/#/oauth-callback?token="
                + URLEncoder.encode(token, "UTF-8")
                + "&username=" + URLEncoder.encode(username, "UTF-8");

        response.sendRedirect(redirectUrl);
    }
}
