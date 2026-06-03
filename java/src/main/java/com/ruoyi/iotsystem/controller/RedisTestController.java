package com.ruoyi.iotsystem.controller;

import com.ruoyi.iotsystem.service.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/redis")
public class RedisTestController {

    @Autowired
    private RedisService redisService;

    // 设置Redis键值
    @PostMapping("/set")
    public String set(@RequestParam String key, @RequestParam String value) {
        redisService.setString(key, value);
        return "Key-value set successfully";
    }

    // 设置Redis键值并指定过期时间(秒)
    @PostMapping("/setWithExpire")
    public String setWithExpire(@RequestParam String key,
                                @RequestParam String value,
                                @RequestParam Long timeout) {
        redisService.setString(key, value, timeout);
        return "Key-value set with expiration: " + timeout + " seconds";
    }

    // 获取Redis键对应的值
    @GetMapping("/get")
    public Object get(@RequestParam String key) {
        Object value = redisService.getString(key);
        if (value == null) {
            return "Key not found or expired";
        }
        return value;
    }

    // 删除Redis键
    @DeleteMapping("/delete")
    public String delete(@RequestParam String key) {
        boolean result = redisService.deleteKey(key);
        if (result) {
            return "Key deleted successfully";
        }
        return "Key not found";
    }

    // 检查Redis键是否存在
    @GetMapping("/exists")
    public String exists(@RequestParam String key) {
        boolean result = redisService.hasKey(key);
        if (result) {
            return "Key exists";
        }
        return "Key not found";
    }
}
