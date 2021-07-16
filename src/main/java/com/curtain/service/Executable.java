package com.curtain.service;



import java.util.Date;

/**
 * @ClassName: Executable
 * @Description: 定时任务执行
 * @Author: 段振宇
 * @Date: 2021/4/14 17:04
 */
public interface Executable {

    /**
     * 任务放在线程池中执行
     * @param task
     */
    void runAsync(Runnable task);

    /**
     * 定时执行
     *
     * @param task 可执行任务
     * @param seconds 定时执行的周期，单位是秒
     */
    Cancelable runPeriodly(Runnable task, long seconds);

    /**
     * 定时执行一次任务
     * @param task
     * @param date
     * @return
     */
    Cancelable runOnceSchedule(Runnable task, Date date);


    /**
     *
     * @param task
     * @param milliSeconds
     * @return
     */
    Cancelable runPeriodlyMilliSeconds(Runnable task, long milliSeconds);

    /**
     * 独占线程执行（新开线程执行）
     *
     * @param task 可执行任务
     */
    void runExclusively(Runnable task, String taskName);

}
