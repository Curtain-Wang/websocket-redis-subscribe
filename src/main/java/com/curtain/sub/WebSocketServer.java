package com.curtain.sub;


import com.alibaba.fastjson.JSON;
import com.curtain.schedule.TaskExecuteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @Author Curtain
 * @Date 2021/5/14 16:49
 * @Description
 */
@ServerEndpoint("/ws")
@Component
@Slf4j
public class WebSocketServer {
    /**concurrent包的线程安全Set，用来存放每个客户端对应的MyWebSocket对象。*/
    private static ConcurrentHashMap<String, ConcurrentHashMap<String,WebSocketServer>> webSocketMap = new ConcurrentHashMap<>();
    /**与某个客户端的连接会话，需要通过它来给客户端发送数据*/
    private Session session;
    /**接收userName*/
    private String sessionId="";
    private static EventPubSub eventPubSub;
    private static TaskExecuteService executeService;
    private static JedisPool jedisPool;

    @Autowired
    public void setEventPubSub(EventPubSub eventPubSub){
        WebSocketServer.eventPubSub = eventPubSub;
    }
    @Autowired
    public void setTaskExecuteService(TaskExecuteService taskExecuteService){
        WebSocketServer.executeService = taskExecuteService;
    }
    @Autowired
    public void setJedisPool(JedisPool jedisPool){
        WebSocketServer.jedisPool = jedisPool;
    }


    /**
     * 连接建立成功调用的方法*/
    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        this.sessionId=session.getId();
        //构造推送数据
        Map pubHeader = new HashMap();
        pubHeader.put("name", "connect_status");
        pubHeader.put("type", "create");
        pubHeader.put("from", "curtain");
        pubHeader.put("time", new Date().getTime() / 1000);
        Map pubPayload = new HashMap();
        pubPayload.put("status", "success");
        Map pubMap = new HashMap();
        pubMap.put("header", pubHeader);
        pubMap.put("payload", pubPayload);
        try {
            sendMessage(JSON.toJSONString(pubMap));
        } catch (IOException e) {
            log.error("sessionId:"+this.sessionId+",网络异常!!!!!!");
        }
    }

    @OnMessage
    public void onMessage(String message){
        Map msgMap = (Map) JSON.parse(message);
        String cmd = (String) msgMap.get("cmd");
        //订阅消息
        if ("subscribe".equals(cmd)){
            List<String> topics = (List<String>) msgMap.get("topic");
            //本地记录订阅信息
            for (int i = 0; i < topics.size(); i++){
                String topic = topics.get(i);
                if (webSocketMap.containsKey(topic)){//有人订阅过了
                    webSocketMap.get(topic).put(this.sessionId, this);
                }else{//之前还没人订阅过，所以需要订阅redis频道
                    ConcurrentHashMap<String, WebSocketServer> map = new ConcurrentHashMap<>();
                    map.put(this.sessionId,this);
                    webSocketMap.put(topic, map);
                    executeService.runAsync(()->eventPubSub.subscribe(topic));
                }
                log.info("sessionId：" + this.sessionId + "，订阅了：" + topic);
            }
        }
        //取消订阅
        if ("unsubscribe".equals(cmd)){
            List<String> topics = (List<String>) msgMap.get("topic");
            //删除本地对应的订阅信息
            for (String topic : topics){
                if (webSocketMap.containsKey(topic)){
                    ConcurrentHashMap<String, WebSocketServer> map = webSocketMap.get(topic);
                    map.remove(this.sessionId);
                    if (map.size() == 0){//如果这个频道没有用户订阅了，则取消订阅该redis频道
                        Jedis jedis = jedisPool.getResource();
                        jedis.publish(topic, "unsubscribe");
                        jedis.close();
                    }
                }
                log.info("sessionId：" + this.sessionId + "，取消订阅了：" + topic);
            }
        }
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose() {
        Iterator iterator = webSocketMap.keySet().iterator();
        while (iterator.hasNext()){
            String topic = (String) iterator.next();
            ConcurrentHashMap<String, WebSocketServer> map = webSocketMap.get(topic);
            map.remove(this.sessionId);
            if (map.size() == 0 ){//如果这个频道没有用户订阅了，则取消订阅该redis频道
                Jedis jedis = jedisPool.getResource();
                jedis.publish(topic, "unsubscribe");
                jedis.close();
            }
        }
        log.info("sessionId："+ this.sessionId + "，断开连接：");
    }



    /**
     *
     * @param session
     * @param error
     */
    @OnError
    public void onError(Session session, Throwable error) {
        log.error("用户错误,sessionId:"+ session.getId() + ",原因:"+error.getMessage());
        error.printStackTrace();
    }

    /**
     * 实现服务器主动推送
     */
    public void sendMessage(String message) throws IOException {
        this.session.getBasicRemote().sendText(message);
    }

    public static void publish(String msg, String topic) throws IOException {
        ConcurrentHashMap<String, WebSocketServer> map = webSocketMap.get(topic);
        if (map != null && map.values() != null){
            for (WebSocketServer webSocketServer : map.values())
                webSocketServer.sendMessage(msg);
        }
    }
}
