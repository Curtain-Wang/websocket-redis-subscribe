package com.curtain.sub;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

import java.io.IOException;
import java.util.Arrays;

/**
 * @Author Curtain
 * @Date 2021/6/7 14:27
 * @Description
 */
@Component
@Slf4j
public class RedisPubSub extends JedisPubSub {

    @Autowired
    private JedisPool jedisPool;

    //订阅
    public void subscribe(String... channels) {
        Jedis jedis = jedisPool.getResource();
        try {
            jedis.subscribe(this, channels);
        } catch (ArithmeticException e) {//取消订阅故意造成的异常
            if (jedis != null)
                jedis.close();
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
            subscribe(channels);
        }
    }

    //模糊订阅
    public void psubscribe(String... channels) {
        Jedis jedis = jedisPool.getResource();
        try {
            jedis.psubscribe(this, channels);
        } catch (ArithmeticException e) {//取消订阅故意造成的异常
            if (jedis != null)
                jedis.close();
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
            psubscribe(channels);
        }
    }

    @Override
    public void onSubscribe(String channel, int subscribedChannels) {
        log.info("subscribe redis channel：" + channel);
    }

    @Override
    public void onPSubscribe(String pattern, int subscribedChannels) {
        log.info("psubscribe redis channel：" + pattern);
    }

    @Override
    public void onPMessage(String pattern, String channel, String message) {
        log.info("receive from redis channal: " + channel + ",pattern: " + pattern + ",message：" + message);
        if ("unsubscribe".equals(message) && channel.equals(pattern)) {//取消订阅
            int a = 0 / 0; //故意造成一个特殊的异常，关闭订阅改频道的jedis连接
            return;
        }
        try {
            WebSocketServer.publish(message, pattern);
            WebSocketServer.publish(message, channel);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(String channel, String message) {
        log.info("receive from redis channal: " + channel + ",message：" + message);
        if ("unsubscribe".equals(message)) {//取消订阅
            int a = 0 / 0; //故意造成一个特殊的异常，关闭订阅改频道的jedis连接
            return;
        }
        try {
            WebSocketServer.publish(message, channel);
        } catch (IOException e) {
            e.printStackTrace();
        }
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
