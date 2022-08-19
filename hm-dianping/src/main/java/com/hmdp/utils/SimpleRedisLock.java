package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @author: lw
 * @date: 2022/8/19 15:59
 * @version: 1.0
 */
public class SimpleRedisLock implements ILock {

    private String name;
    private StringRedisTemplate stringRedisTemplate;
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        // 加载 Lua 配置文件
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(Long timeoutSec) {
        // 获取当前线程id
        String id = ID_PREFIX + Thread.currentThread().getId();
        // 拼接 Redis 的key
        String key = KEY_PREFIX + name;
        // 判断获取锁是否成功
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, id, timeoutSec, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    @Override
    public void unlock() {
        // 调用 Lua 脚本
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(ID_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());
    }

//    @Override
//    public void unlock() {
//        // 获取当前线程id
//        String id = ID_PREFIX + Thread.currentThread().getId();
//        // 判断当前id(value)对应的锁与要释放的锁是不是同一个锁
//        if (id.equals(stringRedisTemplate.opsForValue().get(KEY_PREFIX + name))) {
//            // 如果是同一个锁则释放
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//    }
}
