package com.lin.config;

import com.lin.job.SampleJob;
import com.lin.listener.GlobalJobResultListener;
import jakarta.annotation.PostConstruct;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 * @author linsz
 * @version v1.0
 * @date 2026/4/14 18:58
 */
@Configuration
public class QuartzConfig {



    /**
     * 定义任务
     * @return
     */
    @Bean
    public JobDetail sampleJobDetail(){
        return JobBuilder.newJob(SampleJob.class)
                .withIdentity("sampleJob") // 任务名称
                .withDescription("Sample Job 示例")
                .usingJobData("taskName", "演示任务")
                .storeDurably(true)
                .build();
    }


    @Bean
    public Trigger sampleJobTrigger(JobDetail sampleJobDetail){
        CronScheduleBuilder cronScheduleBuilder = CronScheduleBuilder.cronSchedule("0/10 * * * * ?");
        return TriggerBuilder.newTrigger()
                .forJob(sampleJobDetail)
                .withIdentity("sampleJobTrigger")
                .withDescription("Sample Job 触发器")
                .withSchedule(cronScheduleBuilder)
                .build();
    }
}


/**
 *  trigger
 *  SimpleTrigger  做一些简单的定时任务 轮询 过一段时间后开始无限重试的任务
 *  CronTrigger  定时触发器 用Cron 表达式  日常精确到每天的某个时间点
 *  DailyTimeIntervalTrigger  工作时间触发器神器  如果希望在某个时间段内执行任务，并且任务是按照日历的某个时间段来执行的，那么可以使用这个trigger
 *  CalendarIntervalTrigger  日历间隔触发器  如果希望按照日历的某个时间间隔来执行任务，那么可以使用这个trigger  比如间隔3个月执行一次 完美地解决2月这种问题
 */