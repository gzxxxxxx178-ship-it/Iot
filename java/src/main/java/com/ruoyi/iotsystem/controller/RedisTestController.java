package com.ruoyi.iotsystem.controller;

import com.ruoyi.iotsystem.dto.ApiResponse;
import com.ruoyi.iotsystem.service.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/redis")
public class RedisTestController {

    @Autowired
    private RedisService redisService;

    // 设置 Redis 键值
    @PostMapping("/set")
    public ApiResponse<String> set(@RequestParam String key, @RequestParam String value) {
        redisService.setString(key, value);
        return ApiResponse.success("键值设置成功");
    }

    // 设置 Redis 键值并指定过期时间（秒）
    @PostMapping("/setWithExpire")
    public ApiResponse<String> setWithExpire(@RequestParam String key,
                                              @RequestParam String value,
                                              @RequestParam Long timeout) {
        redisService.setString(key, value, timeout);
        return ApiResponse.success("键值设置成功，过期时间: " + timeout + " 秒");
    }

    // 获取 Redis 键对应的值
    @GetMapping("/get")
    public ApiResponse<Object> get(@RequestParam String key) {
        Object value = redisService.getString(key);
        if (value == null) {
            return ApiResponse.fail("键不存在或已过期");
        }
        return ApiResponse.success(value);
    }

    // 删除 Redis 键
    @DeleteMapping("/delete")
    public ApiResponse<String> delete(@RequestParam String key) {
        boolean result = redisService.deleteKey(key);
        if (result) {
            return ApiResponse.success("键删除成功");
        }
        return ApiResponse.fail("键不存在");
    }

    // 检查 Redis 键是否存在
    @GetMapping("/exists")
    public ApiResponse<Boolean> exists(@RequestParam String key) {
        boolean result = redisService.hasKey(key);
        return ApiResponse.success(result);
    }
}
