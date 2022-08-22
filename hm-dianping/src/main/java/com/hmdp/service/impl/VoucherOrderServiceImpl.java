package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    @Autowired
    private RedisWorker redisWorker;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    private BlockingQueue<VoucherOrder> blockingQueue = new ArrayBlockingQueue<>(1024 * 1024);

    private static final ExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();

    private IVoucherOrderService proxy;

    @PostConstruct  // 在当前类初始化完毕后执行
    private void init() {
        EXECUTOR_SERVICE.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    // 获取队列中订单信息
                    VoucherOrder voucherOrder = blockingQueue.take();
                    // 将订单写入数据库中，并扣减库存
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        // 尝试获取分布式锁
        // 获取用户id
        Long userId = voucherOrder.getUserId();
        // 得到锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 尝试获取分布式锁
        boolean flag = lock.tryLock();  // 无参代表获取失败不等待
        // 获取失败
        if (!flag) {
            log.error("不能重复下单");
            return;
        }
        // 如果获取锁成功
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Result secKillVoucher(String voucherId) {
        // 执行 Lua 脚本判断用户能否下单
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        Long resLong = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId,
                userId.toString());
        int res = resLong.intValue();
        if (res != 0) {
            return Result.fail(res == 1 ? "库存不足" : "您已经购买过此优惠券");
        }
        // 如果能下单，则把下单信息保存到阻塞队列中
        long orderId = redisWorker.nextId("order");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(Long.valueOf(voucherId));
        blockingQueue.add(voucherOrder);
        // 获取动态代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(orderId);
    }


//    @Override
//    public Result secKillVoucher(String voucherId) {
//        // 首先查询优惠券
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//        // 判断秒杀是否已经开始
//        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            // 如果还没开始
//            return Result.fail("秒杀尚未开始");
//        }
//        // 判断秒杀是否结束
//        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
//            // 如果已经结束
//            return Result.fail("秒杀已经结束");
//        }
//        // 查询库存
//        if (seckillVoucher.getStock() < 1) {
//            // 如果库存不足
//            return Result.fail("库存不足");
//        }
//        // 设置订单
//        // 尝试获取分布式锁
//        UserDTO user = UserHolder.getUser();
////        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + user.getId(), stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:" + user.getId());
//        boolean flag = lock.tryLock();  // 无参代表获取失败不等待
//        if (!flag) {
//            return Result.fail("您已经购买过此优惠券");
//        }
//        // 如果获取锁成功
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId, user.getId().toString());
//        } finally {
//            lock.unlock();
//        }
//    }

    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 扣减库存
        boolean flag = seckillVoucherService.update().
                setSql("stock = stock - 1").
                eq("voucher_id", voucherOrder.getVoucherId()).
                gt("stock", 0).update();
        // 订单写入数据库
        save(voucherOrder);
    }
}
