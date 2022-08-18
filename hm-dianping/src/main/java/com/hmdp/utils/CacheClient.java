package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @author: lw
 * @date: 2022/8/18 9:39
 * @version: 1.0
 */

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }


    // 将任意一个 Java 对象转换为 Json 字符串的形式存入 Redis 中，并设置过期时间
    public void setWithTTL(String key, Object object, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(object), time, timeUnit);
    }


    // 将任意一个 Java 对象转换为 Json 字符串的形式存入 Redis 中，并设置逻辑过期时间
    public void setWithLogicalExpire(String key, Object object, Long time, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(object);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    // 以防止缓存穿透的方式查询数据
    public <R> R queryWithPassThrough(String keyPrefix, String id, Class<R> clazz,
                                      Function<String, R> dbFallBack, Long time, TimeUnit timeUnit) {
        // 拼接得到 Redis 中存储的 key
        String key = keyPrefix + id;
        // 去 Redis 缓存中查找
        String json = stringRedisTemplate.opsForValue().get(key);
        // 首先判断 json 是否为空值
        if (StringUtils.isNotBlank(json)) {
            // 若 json 不为空，则进行反序列化后返回
            return JSONUtil.toBean(json, clazz);
        }
        if ("".equals(json)) {
            // 若是空值则直接返回空
            return null;
        }
        // 若不满足上述两种情况，则 json 为 null
        // 去数据库中查询数据
        R r = dbFallBack.apply(id);
        // 查到数据后判断是否为 null
        if (r == null) {
            // 如果为 null，向 Redis 存入空值
            this.setWithTTL(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 如果不为空
        this.setWithTTL(key, r, time, timeUnit);
        return r;
    }

    // 以防止缓存击穿(逻辑过期)的方式查询数据
    public <R> R queryByIdWithLogicalExpire(String keyPrefix, String id, Class<R> clazz, String lockKeyPrefix,
                                            Function<String, R> dbFallBack, Long time, TimeUnit timeUnit) {
        // 拼接得到 Redis 的key
        String key = keyPrefix + id;
        // 去 Redis 中查询数据
        String json = stringRedisTemplate.opsForValue().get(key);
        // 如果 json 是空字符串(热点key一定不为null)
        if ("".equals(json)) {
            return null;
        }
        // 如果 json 不为空，首先反序列化为 Java 对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), clazz);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 判断是否逻辑过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 如果未过期，将数据返回
            return r;
        }
        // 如果过期，则需要缓存重建
        // 首先需要先获取互斥锁
        String lockKey = lockKeyPrefix + id;
        boolean flag = tryToGetLock(lockKey);
        // 如果获取成功
        if (flag) {
            // 首先需要判断此时是否过期
            json = stringRedisTemplate.opsForValue().get(key);
            redisData = JSONUtil.toBean(json, RedisData.class);
            r = JSONUtil.toBean((JSONObject) redisData.getData(), clazz);
            expireTime = redisData.getExpireTime();
            // 如果未过期，将数据返回
            if (expireTime.isAfter(LocalDateTime.now()))  return r;
            // 如果还是过期状态，则新建一个线程来重建缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
               // 查询数据库
                R r1 = dbFallBack.apply(id);
                // 添加到 Redis 缓存中
                this.setWithLogicalExpire(key, r1, time, timeUnit);
                // 释放锁
                releasingLock(lockKey);
            });
        }
        // 未获取成功则直接返回原来的过期信息
        return r;
    }

    // 获取互斥锁
    private boolean tryToGetLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void releasingLock(String key) {
        stringRedisTemplate.delete(key);
    }
}
