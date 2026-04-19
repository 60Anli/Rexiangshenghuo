package com.hmdp.service.impl;

import cn.hutool.bloomfilter.bitMap.LongMap;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    private IVoucherOrderService proxy;

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);
    private static final ExecutorService SECKILL_ORDER_EXCUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    public void init(){
        SECKILL_ORDER_EXCUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {
            while (true){
                
                try {
                    //1获取队列中订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    //2下单(分布式锁)
                    handleVoucherOrder(voucherOrder);

                } catch (Exception e) {
                    log.error("处理订单异常",e);
                }
                
            }


        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //获取用户id
        Long userid = voucherOrder.getUserId();
        //创建锁对象
//        SimpleRedisLock lock = new SimpleRedisLock("order：" + userid, stringRedisTemplate);

        RLock lock = redissonClient.getLock("lock:order" + userid);

        //获取锁
        boolean islock = lock.tryLock();

        //判断是否成功
        if(!islock){
            //获取锁失败
            log.error("不运行重复下单");
        }


        try {
//            // 获取代理对象(事务)
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            proxy.createVoucherOrder(voucherOrder);
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } finally {
            //释放锁
            lock.unlock();
        }
    }



    @Override
    public Result seckillVoucher(Long voucherId) {
        //1执行lua脚本
        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString());

        //2判断结果是否为0（有资格）
        int r = result.intValue();
        if (r != 0) {
            //2.1不为0，没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }


        //2.2 为0，有购买资格，把下单信息保存到阻塞队列

        VoucherOrder voucherOrder = new VoucherOrder();
        // 2.3创建订单id
        Long orderid = redisIdWorker.nextId("order");
        voucherOrder.setId(orderid);
        // 2.4添加客户id
        voucherOrder.setUserId(userId);
        // 2.5添加优惠卷id
        voucherOrder.setVoucherId(voucherId);
        // 2.6放入阻塞队列
        orderTasks.add(voucherOrder);

        //3 获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(orderid);
    }



//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 1.查询优惠卷
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        // 2.判断是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            //尚未开始
//            return Result.fail("秒杀未开始");
//        }
//
//        // 3判断秒杀是否结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            //秒杀已结束
//            return Result.fail("秒杀结束");
//        }
//        // 4.判断库存
//        if (voucher.getStock().intValue()<1) {
//            return Result.fail("库存不足");
//        }
//        Long userid = UserHolder.getUser().getId();
//
//        //创建锁对象
////        SimpleRedisLock lock = new SimpleRedisLock("order：" + userid, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order" + userid);
//
//        //获取锁
//        boolean islock = lock.tryLock();
//
//        //判断是否成功
//        if(!islock){
//            return Result.fail("不允许重复下单");
//        }
//        //获取锁失败
//
//        try {
//            // 获取代理对象
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } catch (IllegalStateException e) {
//            throw new RuntimeException(e);
//        } finally {
//            //释放锁
//            lock.unlock();
//        }
//    }
    @Transactional
    public Result createVoucherOrder(VoucherOrder voucherOrder){
        // 6.一人一单
        // 6.1查询订单
        Integer count = query().eq("voucher_id", voucherOrder.getVoucherId()).eq("user_id", voucherOrder.getUserId()).count();
        // 6.2判断是否存在
        if (count>0){
            log.error("以购买一份");
        }
        // 6.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1") // set stock = stock - 1
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0) // where id = ? and stock > 0
                .update();
        if (!success) {
            // 扣减失败
            log.error("重复下单");
        }
        save(voucherOrder);
        return Result.ok();

    }
}

