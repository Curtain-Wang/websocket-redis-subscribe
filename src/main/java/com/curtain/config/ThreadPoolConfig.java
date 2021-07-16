package com.curtain.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * @ClassName: ThreadPoolConfig
 * @Description: 线程池配置类
 * @Author: 段振宇
 * @Date: 2021/4/14 17:09
 */
@Configuration
public class ThreadPoolConfig {

    @Value("${settings.task-pool.pool-size:50}")
    private Integer taskPoolSize;

    @Value("${settings.work-pool.core-pool-size:40}")
    private Integer workPoolCoreSize;

    @Value("${settings.work-pool.max-pool-size:50}")
    private Integer workPoolMaxSize;

    @Value("${settings.work-pool.queue-capacity:600}")
    private Integer queueCapacity;

    @Bean("taskPoolScheduler")
    public ThreadPoolTaskScheduler getThreadPoolTaskScheduler() {
        ThreadPoolTaskScheduler executor = new ThreadPoolTaskScheduler();

        executor.setPoolSize(taskPoolSize);
        executor.setThreadNamePrefix("schedule-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();

        return executor;
    }

    @Bean("workPoolExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(workPoolCoreSize);
        executor.setMaxPoolSize(workPoolMaxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("task-work-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        return executor;
    }
}
