package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Override
    public Result follow(Long followUserId, Boolean flag) {
        UserDTO user = UserHolder.getUser();
        // 判断是关注还是取关
        if (BooleanUtil.isTrue(flag)) {
            // 关注
            Follow follow = new Follow();
            follow.setFollowUserId(followUserId);
            follow.setUserId(user.getId());
            save(follow);
        } else {
            // 取关
            remove(new QueryWrapper<Follow>().eq("user_id", user.getId()).
                    eq("follow_user_id", followUserId));
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

}
