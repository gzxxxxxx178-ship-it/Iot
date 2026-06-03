package com.ruoyi.iotsystem.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RedisService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /**
     * 设置字符串类型的键值对
     *
     * @param key   键
     * @param value 值
     */
    public void setString(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 设置带过期时间的字符串类型的键值对
     *
     * @param key     键
     * @param value   值
     * @param timeout 过期时间（秒）
     */
    public void setString(String key, String value, long timeout) {
        redisTemplate.opsForValue().set(key, value, timeout, TimeUnit.SECONDS);
    }

    /**
     * 获取指定键的值
     *
     * @param key 键
     * @return 值
     */
    public String getString(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 删除指定键
     *
     * @param key 键
     * @return 是否删除成功
     */
    public Boolean deleteKey(String key) {
        return redisTemplate.delete(key);
    }

    /**
     * 判断指定键是否存在
     *
     * @param key 键
     * @return 是否存在
     */
    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }
}