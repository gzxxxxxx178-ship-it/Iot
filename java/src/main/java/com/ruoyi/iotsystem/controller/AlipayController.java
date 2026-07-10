package com.ruoyi.iotsystem.controller;

import com.alipay.api.AlipayApiException;
import com.ruoyi.iotsystem.dto.ApiResponse;
import com.ruoyi.iotsystem.service.AlipayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/alipay")
public class AlipayController {

    private static final Logger log = LoggerFactory.getLogger(AlipayController.class);

    @Autowired
    private AlipayService alipayService;

    // 创建支付订单：从 SecurityContext 获取当前用户名，调用支付宝预下单，返回二维码和订单号
    @PostMapping("/create")
    public ApiResponse<Map<String, String>> create(@RequestBody Map<String, String> body) throws AlipayApiException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        String amount = body.get("amount");
        String subject = body.getOrDefault("subject", "智慧农业IoT-支付测试");

        if (amount == null || amount.isEmpty()) {
            return ApiResponse.fail("金额不能为空");
        }

        Map<String, String> result = alipayService.createOrder(username, amount, subject);
        return ApiResponse.success(result);
    }

    // 查询订单支付状态：调用支付宝查询接口，已支付则更新数据库，返回订单状态
    @GetMapping("/query")
    public ApiResponse<Map<String, Object>> query(@RequestParam String outTradeNo) throws AlipayApiException {
        Map<String, Object> result = alipayService.queryOrder(outTradeNo);
        return ApiResponse.success(result);
    }

    // 接收支付宝异步支付通知：验签、更新订单。此端点不需要认证，直接返回 "success"/"fail" 给支付宝
    @PostMapping("/notify")
    public String notify(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        Map<String, String[]> requestParams = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : requestParams.entrySet()) {
            String name = entry.getKey();
            String[] values = entry.getValue();
            StringBuilder valueStr = new StringBuilder();
            for (int i = 0; i < values.length; i++) {
                valueStr.append(values[i]);
                if (i < values.length - 1) valueStr.append(",");
            }
            params.put(name, valueStr.toString());
        }

        return alipayService.handleNotify(params);
    }
}
