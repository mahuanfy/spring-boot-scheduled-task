package com.scheduled.dynamic.service.impl;

import com.alibaba.fastjson.JSON;
import com.scheduled.dynamic.bean.ScheduledTaskBean;
import com.scheduled.dynamic.mapper.ScheduledTaskMapper;
import com.scheduled.dynamic.service.ScheduledTaskService;
import com.scheduled.dynamic.service.ScheduledTaskJob;
import com.scheduled.dynamic.task.ScheduledTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 定时任务实现
 *
 * @author 码农猿
 */
@Service
public class ScheduledTaskServiceImpl implements ScheduledTaskService {
    /**
     * 日志
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledTaskServiceImpl.class);

    @Autowired
    private ScheduledTaskMapper taskMapper;
    /**
     * 可重入锁
     */
    private ReentrantLock lock = new ReentrantLock();
    /**
     * 定时任务线程池
     */
    @Autowired
    private ThreadPoolTaskScheduler threadPoolTaskScheduler;
    /**
     * 所有定时任务存放Map
     * key :任务 key
     * value:任务实现
     */
    @Autowired
    @Qualifier(value = "scheduledTaskJobMap")
    private Map<Long, ScheduledTaskJob> scheduledTaskJobMap;

    /**
     * 存放已经启动的任务map
     */
    private Map<Long, ScheduledFuture> scheduledFutureMap = new ConcurrentHashMap<Long, ScheduledFuture>();

    /**
     * 所有任务列表
     */
    @Override
    public List<ScheduledTaskBean> taskList() {
        LOGGER.info(">>>>>> 获取任务列表开始 >>>>>> ");
        //数据库查询所有任务 => 未做分页
        List<ScheduledTaskBean> taskBeanList = taskMapper.getAllTask();
        if (CollectionUtils.isEmpty(taskBeanList)) {
            return new ArrayList<>();
        }

        for (ScheduledTaskBean taskBean : taskBeanList) {
            Long id = taskBean.getId();
            //是否启动标记处理
            taskBean.setStartFlag(this.isStart(id));
        }
        LOGGER.info(">>>>>> 获取任务列表结束 >>>>>> ");
        return taskBeanList;
    }


    /**
     * 根据任务key 启动任务
     */
    @Override
    public Boolean start(Long id) {
        LOGGER.info(">>>>>> 启动任务 {} 开始 >>>>>>", id);
        //添加锁放一个线程启动，防止多人启动多次
        lock.lock();
        LOGGER.info(">>>>>> 添加任务启动锁完毕");
        try {
            //校验是否已经启动
            if (this.isStart(id)) {
                LOGGER.info(">>>>>> 当前任务已经启动，无需重复启动！");
                return false;
            }
            //校验任务是否存在
            if (!scheduledTaskJobMap.containsKey(id)) {
                scheduledTaskJobMap.put(id, new ScheduledTask());
            }
            //根据key数据库获取任务配置信息
            ScheduledTaskBean scheduledTask = taskMapper.getByKey(id);
            //启动任务
            this.doStartTask(scheduledTask);
        } finally {
            // 释放锁
            lock.unlock();
            LOGGER.info(">>>>>> 释放任务启动锁完毕");
        }
        LOGGER.info(">>>>>> 启动任务 {} 结束 >>>>>>", id);
        return true;
    }

    /**
     * 根据 key 停止任务
     */
    @Override
    public Boolean stop(Long id) {
        LOGGER.info(">>>>>> 进入停止任务 {}  >>>>>>", id);
        //当前任务实例是否存在
        boolean taskStartFlag = scheduledFutureMap.containsKey(id);
        LOGGER.info(">>>>>> 当前任务实例是否存在 {}", taskStartFlag);
        if (taskStartFlag) {
            //获取任务实例
            ScheduledFuture scheduledFuture = scheduledFutureMap.get(id);
            //关闭实例
            scheduledFuture.cancel(true);
        }
        LOGGER.info(">>>>>> 结束停止任务 {}  >>>>>>", id);
        return taskStartFlag;
    }

    /**
     * 根据任务key 重启任务
     */
    @Override
    public Boolean restart(Long id) {
        LOGGER.info(">>>>>> 进入重启任务 {}  >>>>>>", id);
        //先停止
        this.stop(id);
        //再启动
        return this.start(id);
    }

    /**
     * 程序启动时初始化  ==> 启动所有正常状态的任务
     */
    @Override
    public void initAllTask(List<ScheduledTaskBean> scheduledTaskBeanList) {
        LOGGER.info("程序启动 ==> 初始化所有任务开始 ！size={}", scheduledTaskBeanList.size());
        if (CollectionUtils.isEmpty(scheduledTaskBeanList)) {
            return;
        }
        for (ScheduledTaskBean scheduledTask : scheduledTaskBeanList) {
            //任务 key
            Long id = scheduledTask.getId();
            //校验是否已经启动
            if (this.isStart(id)) {
                continue;
            }
            //启动任务
            this.doStartTask(scheduledTask);
        }
    }

    /**
     * 执行启动任务
     */
    private void doStartTask(ScheduledTaskBean scheduledTask) {
        //任务key
        Long id = scheduledTask.getId();
        //定时表达式
        String taskCron = scheduledTask.getTaskCron();
        //获取需要定时调度的接口
        ScheduledTaskJob scheduledTaskJob = scheduledTaskJobMap.get(id);
        LOGGER.info(">>>>>> 任务 [ {} ] ,cron={}", scheduledTask.getTaskDesc(), taskCron);
        ScheduledFuture scheduledFuture = threadPoolTaskScheduler.schedule(scheduledTaskJob,
                triggerContext -> {
                    CronTrigger cronTrigger = new CronTrigger(taskCron);
                    return cronTrigger.nextExecutionTime(triggerContext);
                });
        //将启动的任务放入 map
        scheduledFutureMap.put(id, scheduledFuture);
    }

    /**
     * 任务是否已经启动
     */
    private Boolean isStart(Long id) {
        //校验是否已经启动
        if (scheduledFutureMap.containsKey(id)) {
            if (!scheduledFutureMap.get(id).isCancelled()) {
                return true;
            }
        }
        return false;
    }


    /**
     * 打印正在启动的任务
     */
    @Override
    public String printlnTask() {
        LOGGER.info("当前启动的任务项 size {}", scheduledFutureMap.size());
        String printlnTask = JSON.toJSONString(scheduledFutureMap);
        LOGGER.info("当前启动的任务项 {}", printlnTask);
        return printlnTask;
    }
}