package com.ruoyi.iotsystem.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ruoyi.iotsystem.exception.ExternalServiceException;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;

/** 在服务端使用一次性登录凭证向微信兑换OpenID。 */
@Component
public class WechatCodeExchangeClient {

    private static final String CODE_SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";
    private final RestTemplate restTemplate;

    /** 创建带短超时的微信HTTPS客户端。 */
    @Autowired
    public WechatCodeExchangeClient(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }

    /** 使用指定HTTP客户端，供隔离的协议测试注入模拟服务器。 */
    WechatCodeExchangeClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /** 用微信一次性code取得OpenID；不向调用方返回或记录session_key。 */
    public String exchange(String appId, String appSecret, String code) {
        String url = UriComponentsBuilder.fromHttpUrl(CODE_SESSION_URL)
                .queryParam("appid", appId)
                .queryParam("secret", appSecret)
                .queryParam("js_code", code)
                .queryParam("grant_type", "authorization_code")
                .build().encode().toUriString();
        try {
            CodeSessionResponse response = restTemplate.getForObject(url, CodeSessionResponse.class);
            if (response == null || response.openId == null || response.openId.trim().isEmpty()
                    || (response.errorCode != null && response.errorCode.intValue() != 0)) {
                throw new ExternalServiceException("微信登录凭证无效或微信服务暂不可用");
            }
            return response.openId;
        } catch (RestClientException error) {
            throw new ExternalServiceException("连接微信登录服务失败，请稍后重试");
        }
    }

    /** 接收微信code2Session响应，不暴露会话密钥。 */
    private static class CodeSessionResponse {
        @JsonProperty("openid") private String openId;
        @JsonProperty("errcode") private Integer errorCode;
        @JsonProperty("errmsg") private String errorMessage;
    }
}
