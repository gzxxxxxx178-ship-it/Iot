package com.ruoyi.iotsystem.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.ruoyi.iotsystem.entity.PaymentOrderEntity;
import com.ruoyi.iotsystem.repository.PaymentOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

@Service
public class AlipayService {

    private static final Pattern ORDER_NO_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final BigDecimal MAX_ORDER_AMOUNT = new BigDecimal("100000.00");

    private static final Logger log = LoggerFactory.getLogger(AlipayService.class);

    @Value("${alipay.app-id}")
    private String appId;

    @Value("${alipay.private-key}")
    private String privateKey;

    @Value("${alipay.alipay-public-key}")
    private String alipayPublicKey;

    @Value("${alipay.gateway}")
    private String gateway;

    @Value("${alipay.notify-url}")
    private String notifyUrl;

    @Autowired
    private PaymentOrderRepository orderRepository;

    private AlipayClient alipayClient;

    // 初始化支付宝客户端
    @PostConstruct
    public void init() {
        alipayClient = new DefaultAlipayClient(
                gateway, appId, privateKey, "json", "UTF-8",
                alipayPublicKey, "RSA2"
        );
        log.info("支付宝客户端初始化完成, gateway: {}", gateway);
    }

    // 创建支付订单：生成订单号、保存数据库、调用支付宝预下单返回二维码
    public Map<String, String> createOrder(String username, String amountStr, String subject) throws AlipayApiException {
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountStr);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("金额格式无效");
        }
        if (amount.signum() <= 0 || amount.scale() > 2 || amount.compareTo(MAX_ORDER_AMOUNT) > 0) {
            throw new IllegalArgumentException("金额必须大于0、最多两位小数且不超过100000元");
        }
        if (subject == null || subject.trim().isEmpty() || subject.length() > 128) {
            throw new IllegalArgumentException("支付主题不能为空且不得超过128个字符");
        }
        subject = subject.trim();
        String totalAmount = amount.setScale(2, BigDecimal.ROUND_HALF_UP).toString();

        String outTradeNo = generateOrderNo();
        PaymentOrderEntity order = new PaymentOrderEntity(outTradeNo, username, amount, subject);
        orderRepository.save(order);
        log.info("订单创建: outTradeNo={}, amount={}, subject={}", outTradeNo, totalAmount, subject);

        AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
        request.setNotifyUrl(notifyUrl);
        request.setBizContent(
                "{\"out_trade_no\":\"" + outTradeNo + "\"," +
                "\"total_amount\":\"" + totalAmount + "\"," +
                "\"subject\":\"" + escapeJson(subject) + "\"}"
        );

        AlipayTradePrecreateResponse response = alipayClient.execute(request);
        if (!response.isSuccess()) {
            log.error("支付宝预下单失败: code={}, msg={}", response.getCode(), response.getMsg());
            throw new AlipayApiException("支付宝预下单失败: " + response.getMsg());
        }

        log.info("预下单成功: outTradeNo={}", outTradeNo);

        Map<String, String> result = new HashMap<>();
        result.put("outTradeNo", outTradeNo);
        result.put("qrCode", response.getQrCode());
        return result;
    }

    // 查询订单状态：调用支付宝查询接口，已支付则更新数据库
    public Map<String, Object> queryOrder(String outTradeNo) throws AlipayApiException {
        return queryOrder(outTradeNo, null);
    }

    // 查询当前用户所属支付订单，拒绝使用订单号探测他人订单状态
    public Map<String, Object> queryOrder(String outTradeNo, String username) throws AlipayApiException {
        if (outTradeNo == null || !ORDER_NO_PATTERN.matcher(outTradeNo).matches()) {
            throw new IllegalArgumentException("订单号格式无效");
        }
        PaymentOrderEntity ownedOrder = orderRepository.findByOutTradeNo(outTradeNo);
        if (username != null && (ownedOrder == null || !username.equals(ownedOrder.getUsername()))) {
            throw new SecurityException("无权访问该订单");
        }
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizContent("{\"out_trade_no\":\"" + outTradeNo + "\"}");

        AlipayTradeQueryResponse response = alipayClient.execute(request);

        Map<String, Object> result = new HashMap<>();
        result.put("outTradeNo", outTradeNo);

        if (response.isSuccess() && "TRADE_SUCCESS".equals(response.getTradeStatus())) {
            PaymentOrderEntity order = ownedOrder;
            if (order != null && "PENDING".equals(order.getStatus())) {
                order.setStatus("SUCCESS");
                order.setTradeNo(response.getTradeNo());
                order.setPaidAt(LocalDateTime.now());
                orderRepository.save(order);
                log.info("订单支付成功: outTradeNo={}, tradeNo={}", outTradeNo, response.getTradeNo());
            }
            result.put("status", "SUCCESS");
            result.put("tradeNo", response.getTradeNo());
            result.put("amount", response.getTotalAmount());
        } else {
            PaymentOrderEntity order = ownedOrder;
            result.put("status", order != null ? order.getStatus() : "UNKNOWN");
        }

        return result;
    }

    // 处理支付宝异步通知：验签、更新订单状态
    public String handleNotify(Map<String, String> params) {
        log.info("收到支付宝异步通知: outTradeNo={}, tradeStatus={}",
                params.get("out_trade_no"), params.get("trade_status"));

        try {
            boolean verified = AlipaySignature.rsaCheckV1(params, alipayPublicKey, "UTF-8", "RSA2");
            if (!verified) {
                log.error("支付宝通知验签失败");
                return "fail";
            }

            String outTradeNo = params.get("out_trade_no");
            String tradeStatus = params.get("trade_status");
            String tradeNo = params.get("trade_no");

            if ("TRADE_SUCCESS".equals(tradeStatus)) {
                PaymentOrderEntity order = orderRepository.findByOutTradeNo(outTradeNo);
                if (order != null && "PENDING".equals(order.getStatus())) {
                    order.setStatus("SUCCESS");
                    order.setTradeNo(tradeNo);
                    order.setPaidAt(LocalDateTime.now());
                    orderRepository.save(order);
                    log.info("异步通知更新: outTradeNo={} -> SUCCESS", outTradeNo);
                }
            }

            return "success";
        } catch (AlipayApiException e) {
            log.error("支付宝通知处理异常", e);
            return "fail";
        }
    }

    // 生成商户订单号: PAY + 时间戳 + 4位随机数
    private String generateOrderNo() {
        String datePart = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        int random = 1000 + new Random().nextInt(9000);
        return "PAY" + datePart + random;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
