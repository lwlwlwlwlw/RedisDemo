package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.net.http.HttpRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone) {
        // 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 如果不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
        // 如果符合，先生成一个验证码
        String code = RandomUtil.randomString(6);
        // 保存验证码到 session
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 发送验证码
        System.out.println(code + " 发送成功");
        // 返回操作成功信息
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginFormDTO) {
        // 校验手机号和验证码
        if (RegexUtils.isPhoneInvalid(loginFormDTO.getPhone())) {
            return Result.fail("手机号校验失败");
        }
        String code = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + loginFormDTO.getPhone());
        if (code == null || !code.equals(loginFormDTO.getCode())) {
            return Result.fail("验证码错误");
        }
        // 校验成功，判断该用户是否已经存在
        User user = getOne(new QueryWrapper<User>().eq("phone", loginFormDTO.getPhone()));
        if (user == null) {
            // 如果不存在该用户，则创建该用户
            user = createUserByPhone(loginFormDTO.getPhone());
        }
        // 生成随机的 token 作为 key
        String uuid = UUID.randomUUID().toString();
        String tokenKey = RedisConstants.LOGIN_USER_KEY + uuid;
        // user 转为 userDTO
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);
        // userDTO 转为 Map 对象，以便存储到 Redis 中
        Map<String, Object> userDTOMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().
                        setIgnoreNullValue(true).
                        setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        // uuid 为 key，Map 作为 value 存入 Redis 中
        stringRedisTemplate.opsForHash().putAll(tokenKey, userDTOMap);
        // 设置数据有效期
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);
        return Result.ok(RedisConstants.LOGIN_USER_KEY + uuid);
    }

    public User createUserByPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
