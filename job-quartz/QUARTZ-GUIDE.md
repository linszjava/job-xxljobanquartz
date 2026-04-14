# Quartz 定时任务调度完整核心实战指南

## 1. 简介
Quartz 是一个完全由 Java 编写的开源作业调度框架，为在 Java 应用程序中进行作业调度提供了简单却强大的机制。它可以与 J2EE 和 J2SE 应用程序相结合使用，或者单独使用。
Quartz 允许开发人员使用类似于 Cron 的时间表达式来执行作业，并且支持集群环境，保证任务的高可用性。

## 2. 核心概念
在学习 Quartz 之前，必须了解其四大核心组件：
- **Job（任务）**：这是一个接口，只有一个 `execute` 方法，开发者通过实现该接口来定义需要执行的业务逻辑。
- **JobDetail（任务细节）**：用于绑定 Job，并包含关于该 Job 的其他相关信息（例如名称、组名、JobDataMap 传递的参数等）。
- **Trigger（触发器）**：用来决定什么时候执行任务。最常用的触发器有 `SimpleTrigger`（适用于简单的定时、重复执行）和 `CronTrigger`（使用 Cron 表达式进行复杂的调度）。
- **Scheduler（调度器）**：它是 Quartz 的核心，负责管理所有的 Job 和 Trigger。调度器根据配置负责将任务和触发器绑定，并且在触发器被触发时执行任务。

---

## 3. Spring Boot 整合 Quartz 实战

### 3.1 引入依赖
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-quartz</artifactId>
</dependency>
```

### 3.2 编写自定义任务 (Job)
```java
package com.lin.jobquartz.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
public class SampleJob extends QuartzJobBean {

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        // 从 JobDataMap 中获取传递的参数
        String taskName = context.getJobDetail().getJobDataMap().getString("taskName");
        
        log.info("=========== Quartz 任务开始执行 ===========");
        
        try {
            // 这里是具体的业务逻辑
            log.info("执行任务: [{}], 当前机器时间: {}", taskName, 
                     LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                     
            // 模拟业务耗时操作
            Thread.sleep(1000);
            
            // 可以通过 context 将执行结果放回去，留给后续的 Listener 读取
            context.put("result", "业务处理成功！处理了100条数据");
            
        } catch (Exception e) {
            log.error("Quartz 任务执行出现异常!", e);
            // 将异常包装为 JobExecutionException 抛出给框架处理
            throw new JobExecutionException(e);
        }
        
        log.info("=========== Quartz 任务执行结束 ===========");
    }
}
```

### 3.3 配置 Scheduler 和 Trigger
```java
package com.lin.jobquartz.config;

import com.lin.jobquartz.job.SampleJob;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuartzConfig {

    public static final String SAMPLE_JOB_IDENTITY = "SampleJob";

    @Bean
    public JobDetail sampleJobDetail() {
        return JobBuilder.newJob(SampleJob.class)
                .withIdentity(SAMPLE_JOB_IDENTITY, "DEFAULT_GROUP")
                .withDescription("示例 Quartz 任务")
                .usingJobData("taskName", "用户报表统计任务") // 传递参数
                .storeDurably() 
                .build();
    }

    @Bean
    public Trigger sampleJobTrigger(JobDetail sampleJobDetail) {
        // Cron 表达式：每 10 秒执行一次
        CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule("0/10 * * * * ?");

        return TriggerBuilder.newTrigger()
                .forJob(sampleJobDetail)
                .withIdentity(SAMPLE_JOB_IDENTITY + "_Trigger", "DEFAULT_GROUP")
                .withDescription("示例 Quartz 触发器")
                .withSchedule(scheduleBuilder)
                .build();
    }
}
```

---

## 4. 重点：如何知道任务有没有执行？结果是什么？

很多新手刚接触定时任务，写完配置就完了。到了生产环境，**"任务没跑"**、**"任务跑错但不知道"**是最大的灾难。
在 Quartz 中，要追踪任务的执行状态和结果，我们有以下几种核心方案：

### 方案 A：最基础的日志观察法（开发测试必备）
如上面的 `SampleJob` 代码所示，我们在方法的开头、结尾以及异常处打上了 `log.info` 和 `log.error`。
当 Spring Boot 启动后，如果 Trigger 正常触发，你的控制台（以及输出的日志文件）必然会打印：
```text
2026-04-14 19:10:00 INFO  [QuartzScheduler_Worker-1] com.lin.jobquartz.job.SampleJob : =========== Quartz 任务开始执行 ===========
2026-04-14 19:10:00 INFO  [QuartzScheduler_Worker-1] com.lin.jobquartz.job.SampleJob : 执行任务: [用户报表统计任务], 当前机器时间: 2026-04-14 19:10:00
2026-04-14 19:10:01 INFO  [QuartzScheduler_Worker-1] com.lin.jobquartz.job.SampleJob : =========== Quartz 任务执行结束 ===========
```
如果你看不到这三行，说明调度器没触发，或者是 Cron 表达式写错了；如果看到了只出现了一半，或者出现 Exception，说明业务代码逻辑崩溃了。

### 方案 B：高阶实战 —— 使用 JobListener 监听并持久化执行结果
**这是生产级业务系统的标准做法！** 日志虽然好，但是要让运营人员或管理员在后台系统网页上看到“任务执行记录”、“成功与否”、“耗时多久”，我们就必须把**每一次的执行结果写入数据库**。

Quartz 原生提供了监听器机制 `JobListener`，它可以**无侵入地拦截所有任务的生命周期**。

#### 1. 编写一个统一的任务执行结果监听器
```java
package com.lin.jobquartz.listener;

import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.springframework.stereotype.Component;

/**
 * 统一任务执行结果采集监听器
 */
@Slf4j
@Component
public class GlobalJobResultListener implements JobListener {

    @Override
    public String getName() {
        return "GlobalJobResultListener"; // 监听器名字必须有
    }

    // 1. 任务即将被执行前
    @Override
    public void jobToBeExecuted(JobExecutionContext context) {
        String jobName = context.getJobDetail().getKey().getName();
        log.info("[Task-Monitor] 准备执行任务: {}", jobName);
        // 此刻你可以记录任务开始时间 （其实 context.getFireTime() 已经自带了）
    }

    // 2. 任务执行被否决时（被 TriggerListener 拦截了）
    @Override
    public void jobExecutionVetoed(JobExecutionContext context) {
        log.warn("[Task-Monitor] 任务被否决执行: {}", context.getJobDetail().getKey().getName());
    }

    // 3. 任务执行【完成后】的核心处理逻辑：成功？失败？耗时？结果是啥？
    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        String jobName = context.getJobDetail().getKey().getName();
        long runTime = context.getJobRunTime(); // Quartz 自带的，自动算出整个 Job 的执行耗时（毫秒）

        // 获取我们在 Job 中塞入的执行结果字符串
        Object resultObj = context.get("result"); 
        String executeResult = resultObj == null ? "无返回结果" : resultObj.toString();

        if (jobException != null) {
            // 说明你的 execute 方法里面抛出了异常！任务执行失败！
            log.error("[Task-Monitor] ❌ 任务 [{}] 执行失败! 耗时: {}ms. 异常原因: {}", jobName, runTime, jobException.getMessage());
            
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
```

#### 2. 将监听器注册到 Scheduler 调度器中
编写完监听器，必须告诉 Quartz：
打开之前配置的或者是随着全局初始化的时候，将监听器加进去：

```java
import com.lin.jobquartz.listener.GlobalJobResultListener;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Configuration
public class QuartzListenerConfig {

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private GlobalJobResultListener globalJobResultListener;

    @PostConstruct
    public void initListener() throws SchedulerException {
        // 将自定义的监听器注册上去。
        // GroupMatcher.anyGroup() 表示拦截所有的任务
        scheduler.getListenerManager().addJobListener(globalJobResultListener, GroupMatcher.anyGroup());
    }
}
```

**有了这个监听器，无论你写多少个 Job：**
1. 是否调用了？看监听器的 `jobToBeExecuted`。
2. 结果抛异常了没？看监听器 `jobWasExecuted` 里的 `jobException`。
3. 业务结果怎么样？把数据塞进 `context`，在监听器里拿出来存到表里，前端页面就可以直接展示日志流水表了！

---

## 5. 进阶：动态任务调度与状态查询
除了被动监听结果，后台管理系统还需要主动查看目前的任务情况（暂停了还是运行中）。

```java
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DynamicQuartzService {

    @Autowired
    private Scheduler scheduler;

    /**
     * 【状态查询】查询当前任务是运行状态还是暂停状态等？
     */
    public String getJobState(String jobName, String jobGroupName) throws SchedulerException {
        TriggerKey triggerKey = TriggerKey.triggerKey(jobName + "_Trigger", jobGroupName);
        Trigger.TriggerState triggerState = scheduler.getTriggerState(triggerKey);
        // NONE, NORMAL(正常), PAUSED(暂停), COMPLETE(完成), ERROR(错误), BLOCKED(阻塞，通常因为正在执行且加了防并发)
        return triggerState.name(); 
    }

    /**
     * 获取正在执行的任务列表
     */
    public List<JobExecutionContext> getCurrentlyExecutingJobs() throws SchedulerException {
        return scheduler.getCurrentlyExecutingJobs();
    }
    
    // ... 添加、暂停、恢复、删除的方法见下方
    public void addJob(String jobName, String jobGroupName, String cronExpression, Class<? extends Job> jobClass) throws SchedulerException {
        JobDetail jobDetail = JobBuilder.newJob(jobClass).withIdentity(jobName, jobGroupName).build();
        CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(cronExpression);
        CronTrigger trigger = TriggerBuilder.newTrigger().withIdentity(jobName + "_Trigger", jobGroupName).withSchedule(scheduleBuilder).build();
        scheduler.scheduleJob(jobDetail, trigger);
    }
}
```

## 6. 生产级必备：持久化与集群配置（不再仅仅跑在内存）

默认情况下，由于 Quartz 的底层介质为 `RAMJobStore`，每次应用重启后，所有动态添加的任务以及它们的状态记录都会灰飞烟灭。要在真实的生产环境落地并保证高可用，Quartz 必须要接入数据库。

### 6.1 核心数据库表配置
开启数据库持久化后，Quartz 底层强依赖于大约 11 张带有 `QRTZ_` 前缀的内部数据表（如 `QRTZ_JOB_DETAILS`、`QRTZ_TRIGGERS` 等），用来存储调度数据。

在 Spring Boot 中，您只需在 `application.yml` 里做如下配置接入当前系统主控的数据源：

```yaml
spring:
  quartz:
    job-store-type: jdbc # 声明核心介质从 内存(RAM) 切换到 数据库(JDBC)
    jdbc:
      # 非常重要：是否让 Spring Boot 自动初始化 Quartz 需要的 11 张数据库流转表？
      # 【开发测试环境】: 可以设置为 always，让系统自动帮你建表。
      # 【真实生产环境】: 请务必改为 never！你应该自己手动去 Quartz 官方包拿到建表 SQL 脚本在生产库专门跑一遍。设为 never 可以避免应用每次重启都去校验表结构拖慢启动时间，也避免权限越界。
      initialize-schema: always 
    properties:
      # 【架构核心】配置以属性方式存储内容，避免序列化兼容性问题
      org.quartz.jobStore.useProperties: true
      # 【架构核心】开启集群多实例模式！
      org.quartz.jobStore.isClustered: true 
      # 【架构核心】集群实例 ID 自动动态分配策略，配合 isClustered 必须配置
      org.quartz.scheduler.instanceId: AUTO
      # (可选) 如果你用的不是 MySQL，比如 PostgreSQL，可能需要显式指定方言底层代理类
      # org.quartz.jobStore.driverDelegateClass: org.quartz.impl.jdbcjobstore.PostgreSQLDelegate 
```

### 6.2 为什么必须开启 `isClustered: true`？（多节点防重复执行原理）
在微服务或云原生架构下，即使是最小的单体应用，生产上也通常会部署多台节点副本（如同时跑 3 台服务器 A, B, C 控制负载）。
如果大家代码包一模一样，中午 12:00 点一到，三台机器的 Quartz 都会按时响铃被唤醒，去执行同一个“超时订单取消”的任务！**这会导致业务数据库被重复操作 3 次！**

**Quartz 底层是如何解决这一分布式并发灾难的？**
只要你在所有的节点上开启了 `isClustered: true`，并且大家都连着同一个主库，这 3 台机器上的 Quartz 底层管家就不再各自为战，而是会**利用同一张内部数据库表 `QRTZ_LOCKS` 作为 [分布式悲观行锁]**。

1.  将近 12:00 触发前夕，三台机器会同时去争抢 `QRTZ_LOCKS` 里的数据库行锁。
2.  **抢到锁（排它锁）的机器 A**：获得授权，拉取触发器并在本地自己的线程池中平稳跑任务。
3.  **没抢到锁的机器 B, C**：检测到锁被占用，立刻挂起休息，绝不重复干涉业务逻辑。
4.  **自动容灾故障转移 (Failover)**：如果执行“订单取消”的机器 A 刚抢到锁，机房就断电宕机了，它所持有的心跳也会一并断掉。此时，原本在一旁休息的机器 B 就会立刻发现 A “挂了”，从而即刻接管该触发器，保证任务的高可用！

这就是为什么只要用 Quartz，**JDBC 持久化机制 + isClustered 集群锁配置** 是一对永远绑定出现的底层企业级标配！

## 7. 结语与防坑指南
1. **不要在调度触发入口写一跑几个小时的代码**！如果需要，Job 类里仅仅发射一条 MQ（RabbitMQ 或 Kafka），让正宗的消费者慢慢去消耗数据，Quartz 做个发令枪就好了。
2. 同一个任务执行耗时超过了它的 Cron 触发周期（比如 1 分钟跑一次，但它要执行 2 分钟），下次触发会导致重叠运行。记得在任务类上打上 `@DisallowConcurrentExecution` 注解来防止。

---

## 8. 授人以渔：源码与 API 探索技巧（如何知道该传什么参数？）
在面临诸如 “怎样知道这里要传 `GroupMatcher.anyJobGroup()`” 这样的疑问时，死记硬背别人的代码是没有意义的。老手通常是凭借以下 4 个步骤“推导”出来的：

1. **观察方法签名看类型**：当你在 IDE 中敲下 `addJobListener(` 时，IDE 会提示你需要传入 `(JobListener, Matcher<JobKey>... matchers)`。第一个参数是我们自己写的 Listener，但第二个参数是未知的 `Matcher`。
2. **点进源码寻找实现**：按住 `Ctrl / Cmd` 键点击进入 `Matcher` 源码，发现它是个接口。此时利用 IDE 查找接口实现类的快捷键（IDEA 中是 `Ctrl + H` 按看类层次，或 `Option + Cmd + B` 看直接实现类）。
3. **寻找具体实现/工厂类**：在弹出的 `Matcher` 的各种徒子徒孙中，你一眼就能发现几个最明显的派系：`NameMatcher`、`GroupMatcher` 以及您发现的 `EverythingMatcher`。
4. **浏览该类的静态方法（Factory Method）**：双击点进类源码，打开 IDE 的 **Structure** 面板（MacOS: `Cmd + 7`），你会发现里面清一色全是像工厂一样的**静态方法**。
5. **顾名思义选中 API（殊途同归）**：按照我们的需求（想拦截“所有”任务），根据英文原意，你会有如下两个极其优秀的推导结果：
   - **推导 A（最佳语义）**：你点开了 `EverythingMatcher`，发现结构树里有 `allJobs()` 方法。结合上下文，`EverythingMatcher.allJobs()` 在语义上极其完美地表达了“我要监听所有任务”，**这是最顶级的写法！**
   - **推导 B（等价绕弯）**：你点开了 `GroupMatcher`，发现了 `anyJobGroup()`（匹配任意组）。因为所有任务必然属于某个组，匹配了任意组也就等价于匹配了所有任务。这虽然有效，但比起 `EverythingMatcher` 语义上绕了半个圈。

养成**“看入参签名 -> 找底层接口 -> 翻找已知实现类 -> 寻找其静态方法验证语义”**的代码溯源习惯，并且像您一样在推导过程中**货比三家**，以后无论遇到哪个生僻的新型中间件，你都能做到不看百度、仅凭源码就能写出最优雅的方法调用！
