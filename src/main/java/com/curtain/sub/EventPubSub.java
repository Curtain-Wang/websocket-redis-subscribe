package com.curtain.sub;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

import java.io.IOException;

/**
 * @Author Curtain
 * @Date 2021/5/18 16:34
 * @Description 订阅监听redis类
 */
@Component
@Slf4j
public class EventPubSub extends JedisPubSub {

    @Autowired
    private JedisPool jedisPool;

    //订阅
    public void subscribe(String... channels){
        Jedis jedis = jedisPool.getResource();
        try{
            jedis.subscribe(this, channels);
        }catch (ArithmeticException e){//取消订阅故意造成的异常
            if (jedis != null)
                jedis.close();
        }catch (Exception e){
            log.error(e.getMessage());
            if (jedis != null)
                jedis.close();
            //遇到异常后关闭连接重新订阅
            subscribe(channels);
        }
    }

    public void unSubscribe(String channel){
        if (this.getSubscribedChannels() > 0)
            this.unsubscribe(channel);
    }

    @Override
    public void onSubscribe(String channel, int subscribedChannels) {
        log.info("subscribe redis channel：" + channel);
    }

    @Override
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
        System.out.println("unsubscribe redis channel：" + channel);
    }
}
