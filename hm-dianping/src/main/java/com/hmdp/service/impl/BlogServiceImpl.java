package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
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
    private IUserService userService;

    @Override
    public Result queryBlogById(String id) {
        // 根据 id 查询对应的 Blog
        Blog blog = getOne(new QueryWrapper<Blog>().eq("id", id));
        if (blog == null) {
            return Result.fail("博客不存在");
        }
        Long userId = blog.getUserId();
        User user = userService.getOne(new QueryWrapper<User>().eq("id", userId));
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
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
        });
        return Result.ok(records);
    }
}
