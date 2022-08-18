package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryShopList() {
        // 首先去 redis 缓存里面查有没有数据
        String shopTypeList = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_TYPE_KEY);
        // 如果在 redis 缓存中查到了数据，直接返回给前端
        if (StringUtils.isNotBlank(shopTypeList)) {
            List<ShopType> shopTypes = JSONUtil.toList(shopTypeList, ShopType.class);
            return Result.ok(shopTypes);
        }
        // 如果在 redis 缓存中没查到数据，就去数据库查
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        // 如果数据库中也没有，返回错误信息
        if (CollectionUtils.isEmpty(shopTypes)) {
            return Result.fail("查找失败");
        }
        // 如果数据库中查到了，先添加到缓存中，再返回给前端
        // 将 List 转为 Json，然后存储到缓存中
        String shopTypeListJson = JSONUtil.toJsonStr(shopTypes);
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_TYPE_KEY, shopTypeListJson);
        // 返回给前端
        return Result.ok(shopTypes);
    }
}
