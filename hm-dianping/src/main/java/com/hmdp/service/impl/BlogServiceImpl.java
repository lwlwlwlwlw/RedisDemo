package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IUserService userService;

    @Override
    public Result queryBlogById(String id) {
        // 根据 id 查询对应的 Blog
        Blog blog = getOne(new QueryWrapper<Blog>().eq("id", id));
        if (blog == null) {
            return Result.fail("博客不存在");
        }
        // 给 Blog 的 user 信息赋值
        Long userId = blog.getUserId();
        User user = userService.getOne(new QueryWrapper<User>().eq("id", userId));
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
        Boolean flag = isBlogLiked(Long.valueOf(id));
        blog.setIsLike(BooleanUtil.isTrue(flag));
        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> pages = query().orderByDesc("liked").
                page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = pages.getRecords();
        records.forEach(blog -> {
            Long userId = blog.getUserId();
            User user = userService.getOne(new QueryWrapper<User>().eq("id", userId));
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
            blog.setIsLike(BooleanUtil.isTrue(isBlogLiked(blog.getId())));
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        // 根据 id 获取 blog 对象
        Blog blog = getOne(new QueryWrapper<Blog>().eq("id", id));
        if (blog == null)  return Result.fail("博客不存在");
        // 判断用户是否点赞了该博客
        Boolean flag = isBlogLiked(id);
        UserDTO user = UserHolder.getUser();
        if (BooleanUtil.isFalse(flag)) {
            // 如果没点过赞，修改数据库，并将该用户存放到 Redis 缓存中
            boolean isSuccess = update(new UpdateWrapper<Blog>().setSql("liked = liked + 1").eq("id", id));
            if (isSuccess) {
                stringRedisTemplate.opsForSet().add(RedisConstants.BLOG_LIKED_KEY + id, user.getId().toString());
            } else {
                return Result.fail("点赞失败");
            }
        } else {
            // 如果点过赞，则取消点赞，同时将用户从 Redis 缓存中取消
            // 如果没点过赞，修改数据库，并将该用户存放到 Redis 缓存中
            boolean isSuccess = update(new UpdateWrapper<Blog>().setSql("liked = liked - 1").eq("id", id));
            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove(RedisConstants.BLOG_LIKED_KEY + id, user.getId().toString());
            } else {
                return Result.fail("取消失败");
            }
        }
        return Result.ok();
    }

    private Boolean isBlogLiked(Long id) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 判断当前用户是否点过赞
        return stringRedisTemplate.opsForSet().isMember(RedisConstants.BLOG_LIKED_KEY + id,
                user.getId().toString());
    }
}
