package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
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
import java.util.Set;
import java.util.stream.Collectors;

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
        isBlogLiked(blog);
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
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        UserDTO user = UserHolder.getUser();
        if (user == null)  return Result.fail("当前未登录");
        // 根据 id 获取 blog 对象
        Blog blog = getOne(new QueryWrapper<Blog>().eq("id", id));
        if (blog == null)  return Result.fail("博客不存在");
        // 判断用户是否点赞了该博客
        isBlogLiked(blog);
        if (!blog.getIsLike()) {
            // 如果没点过赞，修改数据库，并将该用户存放到 Redis 缓存中
            boolean isSuccess = update(new UpdateWrapper<Blog>().setSql("liked = liked + 1").eq("id", id));
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(RedisConstants.BLOG_LIKED_KEY + id,
                        user.getId().toString(), System.currentTimeMillis());
            } else {
                return Result.fail("点赞失败");
            }
        } else {
            // 如果点过赞，则取消点赞，同时将用户从 Redis 缓存中取消
            // 如果没点过赞，修改数据库，并将该用户存放到 Redis 缓存中
            boolean isSuccess = update(new UpdateWrapper<Blog>().setSql("liked = liked - 1").eq("id", id));
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(RedisConstants.BLOG_LIKED_KEY + id,
                        user.getId().toString());
            } else {
                return Result.fail("取消失败");
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        // 查询 top5 的点赞用户
        Set<String> range = stringRedisTemplate.opsForZSet().range(RedisConstants.BLOG_LIKED_KEY + id, 0, 4);
        if (range == null || range.isEmpty())  return Result.ok();
        List<Long> ids = range.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        List<User> users = userService.list(new QueryWrapper<User>().in("id", ids).
                last("order by field(id, " + idStr + ")"));
        List<UserDTO> userDTOS = users.stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    @Override
    public Result queryBlogByUserId(Integer current, Long userId) {
        Page<Blog> blogPage = page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE),
                new QueryWrapper<Blog>().eq("user_id", userId));
        List<Blog> records = blogPage.getRecords();
        return Result.ok(records);
    }

    private void isBlogLiked(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null)  return;
        // 判断当前用户是否点过赞
        Double score = stringRedisTemplate.opsForZSet().score(RedisConstants.BLOG_LIKED_KEY + blog.getId(),
                user.getId().toString());
        blog.setIsLike(score != null);
    }
}
