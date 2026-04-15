package com.lin.listener;

import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.springframework.context.annotation.Configuration;

/**
 * 全局任务结果监听器  统一任务执行结果采集监听器
 * @author linsz
 * @version v1.0
 * @date 2026/4/14 19:19
 */
@Slf4j
@Configuration
public class GlobalJobResultListener implements JobListener {
    @Override
    public String getName() {
        return "globalJobResultListener";  // 全局监听器名称
    }

    /**
     * 任务被执行时调用
     * @param context
     */
    @Override
    public void jobToBeExecuted(JobExecutionContext context) {
        String JobName = context.getJobDetail().getKey().getName();
        log.info("任务开始执行------>{}",JobName);
        log.info("context: {}",context);
    }

    /**
     * 任务被否决时调用
     * @param context
     */
    @Override
    public void jobExecutionVetoed(JobExecutionContext context) {
        log.warn("[Task-Monitor] 任务被否决执行: {}",
                context.getJobDetail().getKey().getName());
    }

    /**
     * 任务执行完成后调用
     * @param context
     * @param e
     */
    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException e) {

        String jobName = context.getJobDetail().getKey().getName();
        long runTime = context.getJobRunTime(); // Quartz 自带的，自动算出整个 Job 的执行耗时（毫秒）

        // 获取我们在 Job 中塞入的执行结果字符串
        Object resultObj = context.get("result");
        String executeResult = resultObj == null ? "无返回结果" : resultObj.toString();

        if (e != null) {
            // 说明你的 execute 方法里面抛出了异常！任务执行失败！
            log.error("[Task-Monitor] ❌ 任务 [{}] 执行失败! 耗时: {}ms. 异常原因: {}", jobName, runTime, e.getMessage());

            // TODO: 在这里编写 JDBC 或者 MyBatis-Plus 代码，向你的 "sys_job_log" 数据库表插入一条【失败记录】
            // DbUtils.insertLog(jobName, "FAIL", runTime, jobException.getMessage());

        } else {
            // 完美执行成功
            log.info("[Task-Monitor] ✅ 任务 [{}] 执行成功! 耗时: {}ms. 处理结果: {}", jobName, runTime, executeResult);

            // TODO: 在这里编写 JDBC 或者 MyBatis-Plus 代码，向你的 "sys_job_log" 数据库表插入一条【成功记录】
            // DbUtils.insertLog(jobName, "SUCCESS", runTime, executeResult);
        }
    }
}
