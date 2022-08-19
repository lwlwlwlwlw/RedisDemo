package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisWorker redisWorker;

    @Override
    public Result secKillVoucher(String voucherId) {
        // 首先查询优惠券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        // 判断秒杀是否已经开始
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 如果还没开始
            return Result.fail("秒杀尚未开始");
        }
        // 判断秒杀是否结束
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 如果已经结束
            return Result.fail("秒杀已经结束");
        }
        // 查询库存
        if (seckillVoucher.getStock() < 1) {
            // 如果库存不足
            return Result.fail("库存不足");
        }
        // 设置订单
        UserDTO user = UserHolder.getUser();
        synchronized (user.getId().toString().intern()) {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId, user.getId().toString());
        }
    }

    @Override
    @Transactional
    public Result createVoucherOrder(String voucherId, String userId) {
        // 判断该用户是否已经下过单
        VoucherOrder voucherOrder = getOne(new QueryWrapper<VoucherOrder>().eq("user_id", userId).
                eq("voucher_id", voucherId));
        if (voucherOrder != null) {
            // 该用户已经下过单了
            return Result.fail("您已经购买过此优惠券");
        }
        // 扣减库存
        boolean flag = seckillVoucherService.update().
                setSql("stock = stock - 1").
                eq("voucher_id", voucherId).
                gt("stock", 0).update();
        if (!flag) {
            return Result.fail("库存不足");
        }
        // 创建订单
        voucherOrder = new VoucherOrder();
        // 订单id
        long orderId = redisWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 下单用户id
        voucherOrder.setUserId(Long.valueOf(userId));
        // 代金券id
        voucherOrder.setVoucherId(Long.valueOf(voucherId));
        // 订单写入数据库
        save(voucherOrder);
        return Result.ok(orderId);

    }
}
