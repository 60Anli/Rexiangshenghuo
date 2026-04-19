package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryByid(Long id) {
        // 缓存穿透
//        Shop shop = queryWithPassThrough(id);

        //互斥锁解决缓存击穿
        Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存击穿
//        Shop shop = queryWithLogicalExpire(id);

        // 7 返回
        if (shop==null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    public Shop queryWithMutex(Long id){
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // 1 从redis查商铺缓存
        String jsonshop = stringRedisTemplate.opsForValue().get(key);


        // 2 判断是否存在
        if (StrUtil.isNotBlank(jsonshop)){
            // 3 存在则返回
            Shop shop = JSONUtil.toBean(jsonshop, Shop.class);
            return shop;
        }
        // 判断命中的是否是控制
        if (jsonshop!=null){
            return null;
        }
        // 4.缓存重建
        // 4.1获取互斥锁
        String lockkey = "Lock:shop:"+ id;
        Shop shop = null;
        try {
            boolean islock = trylock(lockkey);
            // 4.2判断是否获取成功
            if(!islock){
                // 4.3不成功，则睡眠后重查redix
                Thread.sleep(50);
                return queryWithPassThrough(id);

            }


            // 4.4成功，尝试创建redis缓存
            //再次检查redis缓存中存不存在
            shop = getById(id);
            // 模拟重建延时
            Thread.sleep(200);
            // 5 不存在返回错误
            if (shop == null) {
                //将空值写入redis
                stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);

                return null;
            }
            // 6 存在，写入redis

            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 7 释放互斥锁
            unlock(key);
        }
        // 8 返回
        return shop;
    }

    public Shop queryWithPassThrough(Long id){
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // 1 从redis查商铺缓存
        String jsonshop = stringRedisTemplate.opsForValue().get(key);


        // 2 判断是否存在
        if (StrUtil.isNotBlank(jsonshop)){
            // 3 存在则返回
            Shop shop = JSONUtil.toBean(jsonshop, Shop.class);
            return shop;
        }
        // 判断命中的是否是控制
        if (jsonshop!=null){
            return null;
        }

        // 4 不存在，根据id查数据库
        Shop shop = getById(id);
        // 5 不存在返回错误
        if (shop == null) {
            //将空值写入redis
            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);

            return null;
        }
        // 6 存在，写入redis

        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 7 返回
        return shop;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public Shop queryWithLogicalExpire(Long id){
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // 1 从redis查商铺缓存
        String jsonshop = stringRedisTemplate.opsForValue().get(key);


        // 2 判断是否存在
        if (StrUtil.isBlank(jsonshop)){
            // 3 不存在则返回null
            return null;
        }


        // 4 命中，先把Json反序列化
        RedisData redisdata = JSONUtil.toBean(jsonshop, RedisData.class);
        JSONObject dataObject = (JSONObject)redisdata.getData();
        Shop shop = JSONUtil.toBean(dataObject, Shop.class);
        LocalDateTime expireTime = redisdata.getExpireTime();

        // 5 判断是否逻辑过期
        if(expireTime.isAfter(LocalDateTime.now())){
            // 5.1未过期，返回旧信息
            return shop;
        }

        // 5.2过期，重建缓存

        // 6 缓存重建
        // 6.1 获取互斥锁
        String lockkey = RedisConstants.LOCK_SHOP_KEY+id;
        boolean trylock = trylock(lockkey);
        // 6.2 判断是否获得
        if (!trylock){
            // 6.3 未获取返回旧数据
            return shop;
        }

        // 6.4 获取锁，使用线程池开启独立线程。实现缓存重建
        CACHE_REBUILD_EXECUTOR.submit(()->{
            try {
                //重建缓存
                saveShop2Redis(id,20L);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                // 7 释放锁
                unlock(lockkey);
            }
        });
//        Shop shop_new = getById(id);
//        RedisData redisData_new = new RedisData();
//        redisData_new.setExpireTime(LocalDateTime.now().plusSeconds(RedisConstants.CACHE_SHOP_TTL));
//
//        stringRedisTemplate.opsForValue().set(lockkey,JSONUtil.toJsonStr(shop_new));
//


        // 8 返回
        return shop;
    }



    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {

        // 1 搜索店铺数据
        Shop shop = getById(id);

        Thread.sleep(200);

        // 2 封装逻辑过期对象
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

        // 3 存入Redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));


    }


    public boolean trylock(String key){
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(b);
    }

    public void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null){
            return Result.fail("商店不存在");
        }

        // 1 更新数据库
        updateById(shop);

        // 2 删除缓存
        String key = RedisConstants.CACHE_SHOP_KEY + shop.getId();
        stringRedisTemplate.delete(key);


        return Result.ok();
    }
}
