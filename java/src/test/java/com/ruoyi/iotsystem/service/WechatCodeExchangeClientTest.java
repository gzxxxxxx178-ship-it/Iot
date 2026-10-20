package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.exception.ExternalServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WechatCodeExchangeClientTest {

    /** 通过服务端HTTPS接口兑换OpenID，不向调用方暴露session_key。 */
    @Test
    void exchange_有效code_只返回openid() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("https://api.weixin.qq.com/sns/jscode2session")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"openid\":\"openid-test\",\"session_key\":\"private-key\"}", MediaType.APPLICATION_JSON));
        WechatCodeExchangeClient client = new WechatCodeExchangeClient(restTemplate);

        assertEquals("openid-test", client.exchange("app-id", "app-secret", "one-use-code"));
        server.verify();
    }

    /** 微信拒绝一次性code时不给出身份结果。 */
    @Test
    void exchange_无效code_抛安全错误() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("api.weixin.qq.com/sns/jscode2session")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"errcode\":40029,\"errmsg\":\"invalid code\"}", MediaType.APPLICATION_JSON));
        WechatCodeExchangeClient client = new WechatCodeExchangeClient(restTemplate);

        assertThrows(ExternalServiceException.class, () -> client.exchange("app-id", "app-secret", "bad-code"));
        server.verify();
    }
}
