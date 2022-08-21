package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author: lw
 * @date: 2022/8/19 20:06
 * @version: 1.0
 */

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
        // 配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://192.168.214.128:6379").setPassword("123456");
        // 创建 Redisson 对象
        return Redisson.create(config);
    }

}
