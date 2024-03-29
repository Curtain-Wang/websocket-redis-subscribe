package com.curtain.core;

import com.curtain.config.GetBeanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

import java.util.Arrays;

/**
 * @Author Curtain
 * @Date 2021/6/7 14:27
 * @Description
 */
@Component
@Slf4j
public class RedisPubSub extends JedisPubSub {
    private JedisPool jedisPool = GetBeanUtil.getBean(JedisPool.class);

    //订阅
    @Override
    public void subscribe(String... channels) {
        boolean done = true;
        while (done){
            Jedis jedis = jedisPool.getResource();
            try {
                jedis.subscribe(this, channels);
                done = false;
            } catch (Exception e) {
                log.error(e.getMessage());
                if (jedis != null)
                    jedis.close();
                //遇到异常后关闭连接重新订阅
                log.info("监听遇到异常，四秒后重新订阅频道：");
                Arrays.asList(channels).forEach(s -> {log.info(s);});
                try {
                    Thread.sleep(4000);
                } catch (InterruptedException interruptedException) {
                    interruptedException.printStackTrace();
                }
            }
        }
    }

    //模糊订阅
    @Override
    public void psubscribe(String... channels) {
        boolean done = true;
        while (done){
            Jedis jedis = jedisPool.getResource();
            try {
                jedis.psubscribe(this, channels);
                done = false;
            } catch (Exception e) {
                log.error(e.getMessage());
                if (jedis != null)
                    jedis.close();
                //遇到异常后关闭连接重新订阅
                log.info("监听遇到异常，四秒后重新订阅频道：");
                Arrays.asList(channels).forEach(s -> {log.info(s);});
                try {
                    Thread.sleep(4000);
                } catch (InterruptedException interruptedException) {
                    interruptedException.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onSubscribe(String channel, int subscribedChannels) {
        log.info("subscribe redis channel：" + channel + ", 线程id：" + Thread.currentThread().getId());
    }

    @Override
    public void onPSubscribe(String pattern, int subscribedChannels) {
        log.info("psubscribe redis channel：" + pattern + ", 线程id：" + Thread.currentThread().getId());
    }

    @Override
    public void onPMessage(String pattern, String channel, String message) {
        log.info("receive from redis channal: " + channel + ",pattern: " + pattern + ",message：" + message + ", 线程id：" + Thread.currentThread().getId());
        WebSocketServer.publish(message, pattern);
        WebSocketServer.publish(message, channel);

    }

    @Override
    public void onMessage(String channel, String message) {
        log.info("receive from redis channal: " + channel + ",message：" + message + ", 线程id：" + Thread.currentThread().getId());
        WebSocketServer.publish(message, channel);
    }

    @Override
    public void onUnsubscribe(String channel, int subscribedChannels) {
        log.info("unsubscribe redis channel：" + channel);
    }

    @Override
    public void onPUnsubscribe(String pattern, int subscribedChannels) {
        log.info("punsubscribe redis channel：" + pattern);
    }
}
