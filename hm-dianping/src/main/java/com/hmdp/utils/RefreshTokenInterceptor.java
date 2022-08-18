package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author: lw
 * @date: 2022/8/16 13:36
 * @version: 1.0
 */

public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取请求头中的 tokenKey
        String tokenKey = request.getHeader("authorization");
        // 判断 tokenKey 是否为空，如果是空就直接交给下一级拦截器
        if (tokenKey == null || StringUtils.isBlank(tokenKey)) {
            return true;
        }
        // 在 Redis 中根据请求头中的 tokenKey 获取 UserDTO 对象
        Map<Object, Object> userDTOMap = stringRedisTemplate.opsForHash().entries(tokenKey);
        if (userDTOMap.isEmpty()) {
            return true;
        }
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userDTOMap, new UserDTO(), false);
        // 将 UserDTO 对象存储到 ThreadLocal 中
        UserHolder.saveUser(userDTO);
        // 刷新 User 对象在 Redis 中的有效期
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);
        // 放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        UserHolder.removeUser();
    }


}
