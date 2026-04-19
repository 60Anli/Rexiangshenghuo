package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import com.hmdp.dto.Result;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private StringRedisTemplate stringRedisTemplate;

    private String name;//锁的名称

    public  SimpleRedisLock(String name,StringRedisTemplate stringRedisTemplate){
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }


    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


    @Override
    public boolean tryLock(long timeoutSec) {
        String key = KEY_PREFIX+name;
        //获取线程标识存入value
        String threadid = ID_PREFIX + Thread.currentThread().getId();

        // 获取锁
        Boolean succuess = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, threadid, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(succuess);
    }

    public void unlock(){
        //调用lua脚本
        stringRedisTemplate.execute(UNLOCK_SCRIPT
                , Collections.singletonList(KEY_PREFIX+name)
                ,ID_PREFIX + Thread.currentThread().getId()
                );

    }
//
//    @Override
//    public void unlock() {
//        //获取线程标识
//        String threadid = ID_PREFIX + Thread.currentThread().getId();
//        //获取锁中标识
//        String key = KEY_PREFIX+name;
//        String lock_threadid = stringRedisTemplate.opsForValue().get(key);
//        //判断标识是否一致
//        if(threadid.equals(lock_threadid)){
//            this.stringRedisTemplate.delete(KEY_PREFIX+name);
//        }




}
