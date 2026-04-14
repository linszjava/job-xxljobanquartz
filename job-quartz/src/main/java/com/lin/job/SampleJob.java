package com.lin.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.cglib.core.Local;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 *
 * @author linsz
 * @version v1.0
 * @date 2026/4/14 16:34
 */
@Slf4j
public class SampleJob extends QuartzJobBean {
    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        log.info("任务开始执行------>");
        String taskName = context.getJobDetail().getJobDataMap().getString("taskName");
        log.info("任务开始执行，任务名称：{},时间是{}",taskName,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        log.info("任务执行完毕<----------");

    }
}
