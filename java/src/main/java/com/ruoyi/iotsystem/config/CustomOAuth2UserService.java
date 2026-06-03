package com.ruoyi.iotsystem.config;

import com.ruoyi.iotsystem.entity.UserEntity;
import com.ruoyi.iotsystem.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    @Autowired
    private UserRepository userRepository;

    // 加载OAuth2用户信息，首次登录自动注册
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String provider = userRequest.getClientRegistration().getRegistrationId().toUpperCase();

        Map<String, Object> attributes = oAuth2User.getAttributes();
        String providerId = (String) attributes.get("sub");
        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");
        String avatar = (String) attributes.get("picture");

        // 查是否已有该 OAuth 用户
        UserEntity user = userRepository.findByProviderAndProviderId(provider, providerId);

        if (user == null) {
            // 首次登录，自动注册
            String username = generateUsername(email, name, provider);
            user = new UserEntity(username, email, avatar, provider, providerId);
            userRepository.save(user);
        } else {
            // 更新邮箱和头像（可能变了）
            if (email != null) user.setEmail(email);
            if (avatar != null) user.setAvatar(avatar);
            userRepository.save(user);
        }

        // 把数据库 username 放进 attributes，后续 handler 可以读到
        Map<String, Object> customAttributes = new HashMap<>(attributes);
        customAttributes.put("db_username", user.getUsername());

        return new DefaultOAuth2User(
                oAuth2User.getAuthorities(),
                customAttributes,
                "sub"
        );
    }

    private String generateUsername(String email, String name, String provider) {
        if (email != null && email.contains("@")) {
            String prefix = email.substring(0, email.indexOf('@'));
            if (!userRepository.existsByUsername(prefix)) {
                return prefix;
            }
            // 加随机后缀防冲突
            return prefix + "_" + System.currentTimeMillis() % 10000;
        }
        if (name != null && !name.isEmpty()) {
            String candidate = name.replaceAll("\\s+", "");
            if (!userRepository.existsByUsername(candidate)) {
                return candidate;
            }
            return candidate + "_" + System.currentTimeMillis() % 10000;
        }
        return provider.toLowerCase() + "_user_" + System.currentTimeMillis() % 100000;
    }
}
