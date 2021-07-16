package com.curtain.service;

/**
 * @ClassName: Cancelable
 * @Description: 定时任务返回
 * @Author: 段振宇
 * @Date: 2021/4/14 16:52
 */
public interface Cancelable {

    /**
     * 取消（删除）已创建的定时任务
     * @return
     */
    void cancel();

    /**
     * 定时任务是否已删除
     * @return
     */
    boolean isCancelled();
}
