package com.ruoyi.iotsystem.controller;

import com.alipay.api.AlipayApiException;
import com.ruoyi.iotsystem.dto.ApiResponse;
import com.ruoyi.iotsystem.config.SecurityContextUtils;
import com.ruoyi.iotsystem.service.AlipayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@Tag(name = "支付宝支付", description = "支付宝沙箱支付：下单、查询、异步通知")
@RestController
@RequestMapping("/api/alipay")
public class AlipayController {

    private static final Logger log = LoggerFactory.getLogger(AlipayController.class);

    @Autowired
    private AlipayService alipayService;

    @Operation(summary = "创建支付订单", description = "生成预支付订单，返回支付宝扫码二维码")
    @PostMapping("/create")
    public ApiResponse<Map<String, String>> create(@RequestBody Map<String, String> body) throws AlipayApiException {
        String username = SecurityContextUtils.requireUsername();

        String amount = body.get("amount");
        String subject = body.getOrDefault("subject", "智慧农业IoT-支付测试");

        if (amount == null || amount.trim().isEmpty()) {
            return ApiResponse.fail("金额不能为空");
        }

        Map<String, String> result = alipayService.createOrder(username, amount, subject);
        return ApiResponse.success(result);
    }

    @Operation(summary = "查询支付状态", description = "根据商户订单号查询支付宝支付结果")
    @GetMapping("/query")
    public ApiResponse<Map<String, Object>> query(
            @Parameter(description = "商户订单号") @RequestParam String outTradeNo) throws AlipayApiException {
        Map<String, Object> result = alipayService.queryOrder(
                outTradeNo, SecurityContextUtils.requireUsername());
        return ApiResponse.success(result);
    }

    @Operation(summary = "支付宝异步通知", description = "接收支付宝支付结果异步回调，无需认证")
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
