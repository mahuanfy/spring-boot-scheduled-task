package com.scheduled.dynamic.task;

import com.scheduled.dynamic.service.ScheduledTaskJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 任务 01
 *
 * @author 码农猿
 */
public class ScheduledTask implements ScheduledTaskJob {
    /**
     * 日志
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledTask.class);

    @Override
    public void run() {
        LOGGER.info("****给微信发送消息 ", Thread.currentThread().getName());
    }
}