package com.lin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Quartz 动态任务调度核心服务
 * 提供任务的增删改查、暂停、恢复等动态管理功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicQuartzService {

    private final Scheduler scheduler;

    /**
     * 动态添加并启动定时任务
     *
     * @param jobName        任务名称
     * @param jobGroupName   任务组名
     * @param cronExpression Cron 表达式
     * @param jobClass       具体执行的 Job 实现类
     * @param jobDataMap     需要传递的业务实参 (可选)
     */
    public void addJob(String jobName, String jobGroupName, String cronExpression, Class<? extends Job> jobClass, JobDataMap jobDataMap) throws SchedulerException {
        // 1. 构建 JobDetail
        JobDetail jobDetail = JobBuilder.newJob(jobClass)
                .withIdentity(jobName, jobGroupName)
                .setJobData(jobDataMap != null ? jobDataMap : new JobDataMap())
                .build();

        // 2. 构建 CronTrigger
        CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(cronExpression);
        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(jobName + "_Trigger", jobGroupName)
                .withSchedule(scheduleBuilder)
                .build();

        // 3. 将任务和触发器注册到调度器中，即刻生效
        scheduler.scheduleJob(jobDetail, trigger);
        log.info("成功添加动态任务: {}.{}", jobGroupName, jobName);
    }

    /**
     * 暂停任务
     */
    public void pauseJob(String jobName, String jobGroupName) throws SchedulerException {
        scheduler.pauseJob(JobKey.jobKey(jobName, jobGroupName));
        log.info("成功暂停任务: {}.{}", jobGroupName, jobName);
    }

    /**
     * 恢复被暂停的任务
     */
    public void resumeJob(String jobName, String jobGroupName) throws SchedulerException {
        scheduler.resumeJob(JobKey.jobKey(jobName, jobGroupName));
        log.info("成功恢复任务: {}.{}", jobGroupName, jobName);
    }

    /**
     * 删除任务 (不仅会删除 Job 还会连带删除它的 Trigger)
     */
    public void deleteJob(String jobName, String jobGroupName) throws SchedulerException {
        scheduler.deleteJob(JobKey.jobKey(jobName, jobGroupName));
        log.info("成功删除任务: {}.{}", jobGroupName, jobName);
    }

    /**
     * 立即触发执行一次任务 
     * (仅仅是让后台立刻排队并执行一次，且不改变原有的任何自然调度周期)
     */
    public void triggerJob(String jobName, String jobGroupName, JobDataMap jobDataMap) throws SchedulerException {
        // 如果想在临时触发时覆盖一下参数，可以传 jobDataMap，否则传 null
        if (jobDataMap == null) {
            scheduler.triggerJob(JobKey.jobKey(jobName, jobGroupName));
        } else {
            scheduler.triggerJob(JobKey.jobKey(jobName, jobGroupName), jobDataMap);
        }
        log.info("已下发【立即执行一次】任务指令: {}.{}", jobGroupName, jobName);
    }

    /**
     * 获取任务当前的触发状态
     *
     * @return NONE, NORMAL(正常等待中), PAUSED(已暂停), COMPLETE(不会再触发了), ERROR(异常状态), BLOCKED(正在执行并且加了并发限制)
     */
    public String getJobState(String jobName, String jobGroupName) throws SchedulerException {
        TriggerKey triggerKey = TriggerKey.triggerKey(jobName + "_Trigger", jobGroupName);
        Trigger.TriggerState triggerState = scheduler.getTriggerState(triggerKey);
        return triggerState.name();
    }

    /**
     * 查询当前系统中正在执行的任务列表 (即目前正在抢占线程的方法上下文)
     */
    public List<JobExecutionContext> getCurrentlyExecutingJobs() throws SchedulerException {
        return scheduler.getCurrentlyExecutingJobs();
    }
}
