package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author: lw
 * @date: 2022/8/18 15:06
 * @version: 1.0
 */

@Component
public class RedisWorker {

    // 开始时间戳
    private static final long BEGIN_TIMESTAMP = 1660780800L;

    private static final int COUNT_BITS = 32;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix) {
        // 生成时间戳
        long nowTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowTime - BEGIN_TIMESTAMP;
        // 生成序列号
        // 获取当前时间，精确到天
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long increment = stringRedisTemplate.opsForValue().increment("incr:" + keyPrefix + ":" + now);
        // 拼接并返回
        return timeStamp << COUNT_BITS | increment;
    }

    public static void main(String[] args) {
        LocalDateTime startTime = LocalDateTime.of(2022, 8, 18, 0, 0, 0);
        long second = startTime.toEpochSecond(ZoneOffset.UTC);
        System.out.println(second);
    }


}
