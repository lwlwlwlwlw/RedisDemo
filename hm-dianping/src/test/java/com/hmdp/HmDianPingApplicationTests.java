package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private CacheClient cacheClient;

    @Autowired
    private IShopService shopService;

    @Test
    public void testSaveShop() {
        Shop shop = shopService.getById(1);
        cacheClient.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY + shop.getId(), shop, 10L, TimeUnit.SECONDS);
    }

}
