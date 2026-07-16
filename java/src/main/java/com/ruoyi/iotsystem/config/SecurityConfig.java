package com.ruoyi.iotsystem.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private CustomOAuth2UserService customOAuth2UserService;

    @Autowired
    private OAuth2SuccessHandler oAuth2SuccessHandler;

    @Autowired
    private OAuth2FailureHandler oAuth2FailureHandler;

    @Value("${web.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    // 主安全过滤器链：CORS、CSRF、Session、公开/保护路径、OAuth2、JWT过滤器
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors().configurationSource(corsConfigurationSource())
                .and()
                .csrf().csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .requireCsrfProtectionMatcher(csrfRequestMatcher())
                .and()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .and()
                .exceptionHandling()
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setContentType("application/json;charset=UTF-8");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("{\"code\":401,\"message\":\"未登录或Token已过期\",\"data\":null}");
                })
                .and()
                .authorizeRequests()
                .antMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .antMatchers("/actuator/health").permitAll()
                .antMatchers("/api/auth/**", "/login/oauth2/**", "/oauth2/**", "/api/alipay/notify").permitAll()
                // WebSocket HTTP升级请求进入过滤器链，实际鉴权由一次性票据握手拦截器执行
                .antMatchers("/ws/**").permitAll()
                // Swagger UI & API docs (开发/测试用)
                .antMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-resources/**", "/webjars/**").permitAll()
                .anyRequest().authenticated()
                .and()
                .oauth2Login()
                .userInfoEndpoint().userService(customOAuth2UserService)
                .and()
                .successHandler(oAuth2SuccessHandler)
                .failureHandler(oAuth2FailureHandler)
                .and()
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // 仅对基于浏览器会话的写请求要求CSRF，JWT Bearer请求由令牌本身承担防护
    private RequestMatcher csrfRequestMatcher() {
        return request -> {
            String method = request.getMethod().toUpperCase(Locale.ROOT);
            if ("GET".equals(method) || "HEAD".equals(method)
                    || "OPTIONS".equals(method) || "TRACE".equals(method)) {
                return false;
            }
            String path = request.getRequestURI();
            if (path.startsWith(request.getContextPath() + "/api/auth/")
                    || path.equals(request.getContextPath() + "/api/alipay/notify")) {
                return false;
            }
            String authorization = request.getHeader("Authorization");
            return authorization == null || !authorization.startsWith("Bearer ");
        };
    }

    // CORS配置：只允许配置文件声明的前端来源并支持认证Cookie
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .collect(Collectors.toList()));
        configuration.setAllowCredentials(true);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
