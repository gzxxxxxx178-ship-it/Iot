package com.ruoyi.iotsystem.controller;

import com.alipay.api.AlipayApiException;
import com.ruoyi.iotsystem.service.AlipayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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

    // 创建支付订单，返回二维码
    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody Map<String, String> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        String amount = body.get("amount");
        String subject = body.getOrDefault("subject", "智慧农业IoT-支付测试");

        if (amount == null || amount.isEmpty()) {
            Map<String, String> err = new HashMap<>();
            err.put("message", "金额不能为空");
            return ResponseEntity.badRequest().body(err);
        }

        try {
            Map<String, String> result = alipayService.createOrder(username, amount, subject);
            return ResponseEntity.ok(result);
        } catch (AlipayApiException e) {
            log.error("创建订单失败", e);
            Map<String, String> err = new HashMap<>();
            err.put("message", e.getMessage());
            return ResponseEntity.status(500).body(err);
        }
    }

    // 查询订单支付状态
    @GetMapping("/query")
    public ResponseEntity<?> query(@RequestParam String outTradeNo) {
        try {
            Map<String, Object> result = alipayService.queryOrder(outTradeNo);
            return ResponseEntity.ok(result);
        } catch (AlipayApiException e) {
            log.error("查询订单失败", e);
            Map<String, String> err = new HashMap<>();
            err.put("message", e.getMessage());
            return ResponseEntity.status(500).body(err);
        }
    }

    // 接收支付宝异步支付通知
    @PostMapping("/notify")
    public String notify(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        Map<String, String[]> requestParams = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : requestParams.entrySet()) {
            String name = entry.getKey();
            String[] values = entry.getValue();
            String valueStr = "";
            for (int i = 0; i < values.length; i++) {
                valueStr = (i == values.length - 1) ? valueStr + values[i] : valueStr + values[i] + ",";
            }
            params.put(name, valueStr);
        }

        return alipayService.handleNotify(params);
    }
}
