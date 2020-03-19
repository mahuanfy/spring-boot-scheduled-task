package com.scheduled.dynamic.config;

import com.scheduled.dynamic.bean.ScheduledTaskBean;
import com.scheduled.dynamic.mapper.ScheduledTaskMapper;
import com.scheduled.dynamic.service.ScheduledTaskJob;
import com.scheduled.dynamic.task.ScheduledTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class ScheduledTaskConfig {
    @Autowired
    private ScheduledTaskMapper taskMapper;

    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledTaskConfig.class);

    @Bean
    public ThreadPoolTaskScheduler threadPoolTaskScheduler() {
        LOGGER.info("创建定时任务调度线程池 start");
        ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
        threadPoolTaskScheduler.setPoolSize(20);
        threadPoolTaskScheduler.setThreadNamePrefix("taskExecutor-");
        threadPoolTaskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        threadPoolTaskScheduler.setAwaitTerminationSeconds(60);
        LOGGER.info("创建定时任务调度线程池 end");
        return threadPoolTaskScheduler;
    }

    /**
     * 初始化定时任务Map
     * key :任务key
     * value : 执行接口实现
     */
    @Bean(name = "scheduledTaskJobMap")
    public Map<Long, ScheduledTaskJob> scheduledTaskJobMap() {
        List<ScheduledTaskBean> taskBeanList = taskMapper.getAllTask();

        if (taskBeanList.size() <= 0) {
            return new ConcurrentHashMap<>();
        }
        Map<Long, ScheduledTaskJob> scheduledTaskJobMap = new ConcurrentHashMap<>();
        for (ScheduledTaskBean scheduledTaskBean : taskBeanList) {
            scheduledTaskJobMap.put(scheduledTaskBean.getId(), new ScheduledTask());
        }
        return scheduledTaskJobMap;
    }
}
