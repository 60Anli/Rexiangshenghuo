package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    /**
     * 开始时间戳
     */
    private static final Long BEGIN_TIMESTAMP = 1640995200L;
    /**
     * 序列号位数
     */
    private static final Long COUNT_BITS = 32L;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public Long nextId(String keyprefix){
        // 1生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long second = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = second-BEGIN_TIMESTAMP;


        // 2生成序列号
        // 2.1获取当前日期，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2自增长
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyprefix + ":" + date);

        // 3拼接
        return timestamp << COUNT_BITS | count;
    }

}
