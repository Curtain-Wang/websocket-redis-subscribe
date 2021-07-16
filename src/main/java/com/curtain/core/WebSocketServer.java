package com.curtain.core;

import com.alibaba.fastjson.JSON;
import com.curtain.config.WebsocketProperties;
import com.curtain.service.Cancelable;
import com.curtain.service.impl.TaskExecuteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
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
    /**
     * concurrent包的线程安全Set，用来存放每个客户端对应的MyWebSocket对象。
     */
    private static volatile ConcurrentHashMap<String, ConcurrentHashMap<String, WebSocketServer>> webSocketMap = new ConcurrentHashMap<>();
    /**
     * 存放psub的事件
     **/
    private static volatile ConcurrentHashMap<String, ConcurrentHashMap<String, WebSocketServer>> pWebSocketMap = new ConcurrentHashMap<>();
    /**
     * 存放topic(pattern)-对应的RedisPubsub
     */
    private static volatile ConcurrentHashMap<String, RedisPubSub> redisPubSubMap = new ConcurrentHashMap<>();
    /**
     * 与某个客户端的连接会话，需要通过它来给客户端发送数据
     */
    private Session session;
    private String sessionId = "";
    //要注入的对象
    private static TaskExecuteService executeService;
    private static WebsocketProperties properties;

    private Cancelable cancelable;

    @Autowired
    public void setTaskExecuteService(TaskExecuteService taskExecuteService) {
        WebSocketServer.executeService = taskExecuteService;
    }

    @Autowired
    public void setWebsocketProperties(WebsocketProperties properties) {
        WebSocketServer.properties = properties;
    }

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        this.sessionId = session.getId();
        //构造推送数据
        Map pubHeader = new HashMap();
        pubHeader.put("name", "connect_status");
        pubHeader.put("type", "create");
        pubHeader.put("from", "pubsub");
        pubHeader.put("time", new Date().getTime() / 1000);
        Map pubPayload = new HashMap();
        pubPayload.put("status", "success");
        Map pubMap = new HashMap();
        pubMap.put("header", pubHeader);
        pubMap.put("payload", pubPayload);
        sendMessage(JSON.toJSONString(pubMap));
        cancelable = executeService.runPeriodly(() -> {
            try {
                if (cancelable != null && !session.isOpen()) {
                    log.info("断开连接，停止发送ping");
                    cancelable.cancel();
                } else {
                    String data = "ping";
                    ByteBuffer payload = ByteBuffer.wrap(data.getBytes());
                    session.getBasicRemote().sendPing(payload);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, properties.getPeriod());

    }

    @OnMessage
    public void onMessage(String message) {
        synchronized (session) {
            Map msgMap = (Map) JSON.parse(message);
            String cmd = (String) msgMap.get("cmd");
            //订阅消息
            if ("subscribe".equals(cmd)) {
                List<String> topics = (List<String>) msgMap.get("topic");
                //本地记录订阅信息
                for (int i = 0; i < topics.size(); i++) {
                    String topic = topics.get(i);
                    log.info("============================subscribe-start============================");
                    log.info("sessionId：" + this.sessionId + "，开始订阅：" + topic);
                    if (webSocketMap.containsKey(topic)) {//有人订阅过了
                        webSocketMap.get(topic).put(this.sessionId, this);
                    } else {//之前还没人订阅过，所以需要订阅redis频道
                        ConcurrentHashMap<String, WebSocketServer> map = new ConcurrentHashMap<>();
                        map.put(this.sessionId, this);
                        webSocketMap.put(topic, map);
                        new Thread(() -> {
                            RedisPubSub redisPubSub = new RedisPubSub();
                            //存入map
                            redisPubSubMap.put(topic, redisPubSub);
                            redisPubSub.subscribe(topic);
                        }).start();
                    }
                    log.info("sessionId：" + this.sessionId + "，完成订阅：" + topic);
                    log();
                    log.info("============================subscribe-end============================");
                }
            }
            //psubscribe
            if ("psubscribe".equals(cmd)) {
                List<String> topics = (List<String>) msgMap.get("topic");
                //本地记录订阅信息
                for (int i = 0; i < topics.size(); i++) {
                    String topic = topics.get(i);
                    log.info("============================psubscribe-start============================");
                    log.info("sessionId：" + this.sessionId + "，开始模糊订阅：" + topic);
                    if (pWebSocketMap.containsKey(topic)) {//有人订阅过了
                        pWebSocketMap.get(topic).put(this.sessionId, this);
                    } else {//之前还没人订阅过，所以需要订阅redis频道
                        ConcurrentHashMap<String, WebSocketServer> map = new ConcurrentHashMap<>();
                        map.put(this.sessionId, this);
                        pWebSocketMap.put(topic, map);
                        new Thread(() -> {
                            RedisPubSub redisPubSub = new RedisPubSub();
                            //存入map
                            redisPubSubMap.put(topic, redisPubSub);
                            redisPubSub.psubscribe(topic);
                        }).start();
                    }
                    log.info("sessionId：" + this.sessionId + "，完成模糊订阅：" + topic);
                    log();
                    log.info("============================psubscribe-end============================");
                }
            }
            //取消订阅
            if ("unsubscribe".equals(cmd)) {
                List<String> topics = (List<String>) msgMap.get("topic");
                //删除本地对应的订阅信息
                for (String topic : topics) {
                    log.info("============================unsubscribe-start============================");
                    log.info("sessionId：" + this.sessionId + "，开始删除订阅：" + topic);
                    if (webSocketMap.containsKey(topic)) {
                        ConcurrentHashMap<String, WebSocketServer> map = webSocketMap.get(topic);
                        map.remove(this.sessionId);
                        if (map.size() == 0) {//如果这个频道没有用户订阅了，则取消订阅该redis频道
                            webSocketMap.remove(topic);
                            redisPubSubMap.get(topic).unsubscribe(topic);
                        }
                    }
                    if (pWebSocketMap.containsKey(topic)) {
                        ConcurrentHashMap<String, WebSocketServer> map = pWebSocketMap.get(topic);
                        map.remove(this.sessionId);
                        if (map.size() == 0) {//如果这个频道没有用户订阅了，则取消订阅该redis频道
                            pWebSocketMap.remove(topic);
                            redisPubSubMap.get(topic).punsubscribe(topic);
                        }
                    }
                    log.info("sessionId：" + this.sessionId + "，完成删除订阅：" + topic);
                    log();
                    log.info("============================unsubscribe-end============================");
                }
            }
        }
    }

    @OnMessage
    public void onPong(PongMessage pongMessage) {
        try {
            log.debug(new String(pongMessage.getApplicationData().array(), "utf-8") + "接收到pong");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose() {
        synchronized (session) {
            log.info("============================onclose-start============================");
            //删除订阅
            Iterator iterator = webSocketMap.keySet().iterator();
            while (iterator.hasNext()) {
                String topic = (String) iterator.next();
                ConcurrentHashMap<String, WebSocketServer> map = webSocketMap.get(topic);
                map.remove(this.sessionId);
                if (map.size() == 0) {//如果这个频道没有用户订阅了，则取消订阅该redis频道
                    webSocketMap.remove(topic);
                    redisPubSubMap.get(topic).unsubscribe(topic);
                }
            }
            //删除模糊订阅
            Iterator iteratorP = pWebSocketMap.keySet().iterator();
            while (iteratorP.hasNext()) {
                String topic = (String) iteratorP.next();
                ConcurrentHashMap<String, WebSocketServer> map = pWebSocketMap.get(topic);
                map.remove(this.sessionId);
                if (map.size() == 0) {//如果这个频道没有用户订阅了，则取消订阅该redis频道
                    pWebSocketMap.remove(topic);
                    redisPubSubMap.get(topic).punsubscribe(topic);
                }
            }
            log.info("sessionId：" + this.sessionId + "，断开连接：");
            //debug
            log();
            log.info("============================onclose-end============================");
        }
    }


    /**
     * @param session
     * @param error
     */
    @OnError
    public void onError(Session session, Throwable error) {
        synchronized (session) {
            log.info("============================onError-start============================");
            log.error("用户错误,sessionId:" + session.getId() + ",原因:" + error.getMessage());
            error.printStackTrace();
            log.info("关闭错误用户对应的连接");
            //删除订阅
            Iterator iterator = webSocketMap.keySet().iterator();
            while (iterator.hasNext()) {
                String topic = (String) iterator.next();
                ConcurrentHashMap<String, WebSocketServer> map = webSocketMap.get(topic);
                map.remove(this.sessionId);
                if (map.size() == 0) {//如果这个频道没有用户订阅了，则取消订阅该redis频道
                    webSocketMap.remove(topic);
                    redisPubSubMap.get(topic).unsubscribe(topic);
                }
            }
            //删除模糊订阅
            Iterator iteratorP = pWebSocketMap.keySet().iterator();
            while (iteratorP.hasNext()) {
                String topic = (String) iteratorP.next();
                ConcurrentHashMap<String, WebSocketServer> map = pWebSocketMap.get(topic);
                map.remove(this.sessionId);
                if (map.size() == 0) {//如果这个频道没有用户订阅了，则取消订阅该redis频道
                    pWebSocketMap.remove(topic);
                    redisPubSubMap.get(topic).punsubscribe(topic);
                }
            }
            log.info("完成错误用户对应的连接关闭");
            //debug
            log();
            log.info("============================onError-end============================");
        }
    }

    /**
     * 实现服务器主动推送
     */
    public void sendMessage(String message) {
        synchronized (session) {
            try {
                this.session.getBasicRemote().sendText(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void publish(String msg, String topic) {
        ConcurrentHashMap<String, WebSocketServer> map = webSocketMap.get(topic);
        if (map != null && map.values() != null) {
            for (WebSocketServer webSocketServer : map.values())
                webSocketServer.sendMessage(msg);
        }
        map = pWebSocketMap.get(topic);
        if (map != null && map.values() != null) {
            for (WebSocketServer webSocketServer : map.values())
                webSocketServer.sendMessage(msg);
        }
    }

    private void log() {
        log.info("<<<<<<<<<<<完成操作后，打印订阅信息开始>>>>>>>>>>");
        Iterator iterator1 = webSocketMap.keySet().iterator();
        while (iterator1.hasNext()) {
            String topic = (String) iterator1.next();
            log.info("topic：" + topic);
            Iterator iterator2 = webSocketMap.get(topic).keySet().iterator();
            while (iterator2.hasNext()) {
                String session = (String) iterator2.next();
                log.info("订阅" + topic + "的sessionId：" + session);
            }
        }
        log.info("<<<<<<<<<<<完成操作后，打印订阅信息结束>>>>>>>>>>");
    }
}
