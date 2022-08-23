package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean flag) {
        UserDTO user = UserHolder.getUser();
        // 判断是关注还是取关
        if (BooleanUtil.isTrue(flag)) {
            // 关注，关注后存入 Redis 中
            Follow follow = new Follow();
            follow.setFollowUserId(followUserId);
            follow.setUserId(user.getId());
            boolean isSuccess = save(follow);
            if (isSuccess) {
                // 存入 Redis 集合中
                stringRedisTemplate.opsForSet().add(RedisConstants.FOLLOWS_KEY + user.getId(),
                        String.valueOf(followUserId));
            }
        } else {
            // 取关
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id", user.getId()).
                    eq("follow_user_id", followUserId));
            if (isSuccess) {
                // 从 Set 集合中移除
                // 存入 Redis 集合中
                stringRedisTemplate.opsForSet().remove(RedisConstants.FOLLOWS_KEY + user.getId(),
                        String.valueOf(followUserId));
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        UserDTO user = UserHolder.getUser();
        Follow follow = getOne(new QueryWrapper<Follow>().eq("user_id", user.getId()).
                eq("follow_user_id", followUserId));
        if (follow == null) {
            return Result.ok(false);
        } else {
            return Result.ok(true);
        }
    }

    @Override
    public Result getCommonFollows(Long userId) {
        // 从 Redis 缓存中取出所有本用户和目标用户的关注列表的交集
        UserDTO user = UserHolder.getUser();
        Set<String> commonFollows = stringRedisTemplate.opsForSet().intersect(RedisConstants.FOLLOWS_KEY + user.getId(),
                RedisConstants.FOLLOWS_KEY + userId);
        // 如果没有交集，直接返回
        if (commonFollows == null || commonFollows.isEmpty())  return Result.ok();
        // 先将 Set 集合转为 List
        List<Long> commonFollowsIdList = commonFollows.stream().map(Long::valueOf).collect(Collectors.toList());
        // 根据 id 的 List 查出所有用户信息
        List<User> users = userService.list(new QueryWrapper<User>().in("id", commonFollowsIdList));
        // User 转为 UserDTO，防止信息泄露
        List<UserDTO> userDTOS = users.stream().map(user1 -> BeanUtil.copyProperties(user1, UserDTO.class)).
                collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

}
