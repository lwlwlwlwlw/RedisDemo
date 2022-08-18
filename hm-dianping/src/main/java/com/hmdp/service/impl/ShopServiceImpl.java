package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CacheClient cacheClient;

    @Override
    public Result queryById(String id) throws InterruptedException {
        // 用防止缓存击穿的方式查找数据
        Shop shop = cacheClient.queryByIdWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY, id, Shop.class,
                RedisConstants.LOCK_SHOP_KEY, this::getById, RedisConstants.CACHE_SHOP_TTL,
                TimeUnit.SECONDS);
        if (shop == null)  return Result.fail("店铺不存在");
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        if (shop.getId() == null)  {
            return Result.fail("店铺 id 不能为空");
        }
        // 先更新数据库
        updateById(shop);
        // 再删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

//    // 用防止缓存穿透及互斥锁的方式查找数据
//    public Shop queryShopByIdWithMutex(String id) throws InterruptedException {
//        // 从 redis 缓存中查询数据是否存在
//        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
//        // 如果 redis 缓存中存在数据，直接返回
//        if (StringUtils.isNotBlank(shopJson)) {
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        if ("".equals(shopJson)) {
//            // 命中的是空值则返回 null
//            return null;
//        }
//        // 如果 redis 缓存中不存在数据
//        // 获取互斥锁
//        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
//        boolean flag = tryLock(lockKey);
//        if (!flag) {
//            // 获取失败，休眠一段时间后继续获取
//            Thread.sleep(50);
//            return queryShopByIdWithMutex(id);
//        }
//        // 获取成功，首先检查一遍缓存中是否有数据了，如果还没有就去数据库查询数据
//        String shopStr = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
//        if (StringUtils.isNotBlank(shopStr)) {
//            return JSONUtil.toBean(shopStr, Shop.class);
//        }
//        if ("".equals(shopStr)) {
//            // 命中的是空值则返回 null
//            return null;
//        }
//        // 去数据库查
//        Shop shop = getById(id);
//        // 如果数据库没有，则将空信息添加到 redis 缓存中，
//        if (shop == null) {
//            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//        }
//        // 如果在数据库中查询数据成功，添加到 redis 缓存中并返回
//        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        // 释放互斥锁
//        unlock(lockKey);
//        return shop;
//    }

//    // 用防止缓存穿透及逻辑过期的方式查找数据
//    public Shop queryShopByIdWithLogicalExpire(String id) throws InterruptedException {
//        // 从 redis 缓存中查询数据是否存在
//        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
//        // 如果 redis 缓存中数据为空，直接返回
//        if (StringUtils.isBlank(shopJson)) {
//            return null;
//        }
//        // 如果 redis 缓存中存在数据
//        // 判断数据是否过期
//        // 先将Json字符串转化为对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        Shop shop = (Shop) redisData.getData();
//        LocalDateTime expireTime = redisData.getExpireTime();
//        // 判断数据是否过期
//        if (expireTime.isAfter(LocalDateTime.now())) {
//            // 未过期
//            return shop;
//        }
//        // 过期了
//        // 获取互斥锁
//        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
//        boolean flag = tryLock(lockKey);
//        // 获取互斥锁成功，开启独立线程
//        if (flag) {
//            // 再次检测一下缓存是否过期
//            shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
//            redisData = JSONUtil.toBean(shopJson, RedisData.class);
//            shop = (Shop) redisData.getData();
//            expireTime = redisData.getExpireTime();
//            if (expireTime.isAfter(LocalDateTime.now()))  return shop;
//            // 如果还是过期，开启独立线程，将新数据增添到 Redis 中
//            CACHE_REBUILD_EXECUTOR.submit(() -> {
//                saveShopToRedis(id, 20L);
//                unlock(lockKey);
//            });
//        }
//        return shop;
//    }



}














