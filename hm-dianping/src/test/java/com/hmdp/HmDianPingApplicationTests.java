package com.hmdp;

import cn.hutool.core.collection.AvgPartition;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private CacheClient cacheClient;

    @Autowired
    private IShopService shopService;

    @Autowired
    private RedisWorker redisWorker;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private ExecutorService executorService = Executors.newFixedThreadPool(500);

    @Test
    public void testSaveShop() {
        for (int i = 0; i < 14; i++) {
            Shop shop = shopService.getById(i + 1);
            cacheClient.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY + shop.getId(),
                    shop, 10L, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testIdWorker() {
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisWorker.nextId("order");
                System.out.println("id = " + id);
            }
        };
        for (int i = 0; i < 300; i++) {
            executorService.submit(task);
        }
    }

    @Test
    public void loadShopData() {
        // 查询店铺信息
        List<Shop> shopList = shopService.list();
        // 店铺信息按照 type_id 分组
        Map<Long, List<Shop>> map = shopList.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 分批完成存储
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            String key = RedisConstants.SHOP_GEO_KEY + entry.getKey();
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            for (Shop shop : value) {
                locations.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }

}
