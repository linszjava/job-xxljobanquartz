# XXL-JOB 分布式任务调度系统完整教程

## 1. 核心架构认知：大脑与手脚分离
与 Quartz 将所有逻辑揉进你自己的代码不同，XXL-JOB 采用了经典的 **中心化 C/S 架构**。在开始之前，你必须牢记它拆分成了两个不相干的项目：
- **调度中心 (Admin)**：大脑。它是个纯 Web 管理后台，提供可视化界面，负责算时间、保管代码在哪台机器上、一到时间就按动发令枪发射 HTTP 请求。
- **执行器 (Executor)**：手脚。也就是**你自己写的 Spring Boot 业务代码**。里面只有纯粹的业务逻辑（比如清算订单），并且随时监听大脑发过来的 HTTP 指令。

---

## 2. 第一步：搭建并启动“最强大脑”(调度中心)

调度中心本身就是作者写好的一个独立的 Spring Boot 开源 Web 项目，一般由公司的架构师或运维统一跑起来即可。

1. **下载源码**：前往 Github (xuxueli/xxl-job) 或者 Gitee 下载最新的 release 源码。
2. **初始化数据库**：在源码的 `doc/db/tables_xxl_job.sql` 目录找到 SQL 文件，在你自己的 MySQL 里建一个名叫 `xxl_job` 的数据库并跑一遍这个脚本。
3. **修改配置并启动**：
   用 IDEA 打开下载好的项目，找到 `xxl-job-admin` 模块下的 `application.properties`，改成您自己的数据库账号：
   ```properties
   spring.datasource.url=jdbc:mysql://127.0.0.1:3306/xxl_job?useUnicode=true&characterEncoding=UTF-8&autoReconnect=true&serverTimezone=Asia/Shanghai
   spring.datasource.username=root
   spring.datasource.password=123456
   ```
4. **访问可视化页面**：
   运行该项目的主类 `XxlJobAdminApplication`，打开浏览器访问 `http://localhost:8080/xxl-job-admin`。
   > 默认账号：`admin` 
   > 默认密码：`123456`

---

## 3. 第二步：在你的业务系统中集成“手脚”(执行器)

接下来咱们回到**你自己的 Spring Boot 业务开发项目**，也就是要做实事干真正业务的地方。

### 3.1 引入依赖
```xml
<dependency>
    <groupId>com.xuxueli</groupId>
    <artifactId>xxl-job-core</artifactId>
    <version>2.4.0</version> <!-- 强烈建议保持跟您刚才起步的调度中心版本完全一致 -->
</dependency>
```

### 3.2 配置文件 (application.yml)
告诉这双“手脚”：大脑在哪里？以及它自己叫什么名字？
```yaml
xxl:
  job:
    admin:
      address: http://127.0.0.1:8080/xxl-job-admin # 调度中心(大脑)的通信地址
    executor:
      appname: my-business-executor # 执行器的唯一代号(非常重要！调度中心要在前端靠这个绑定你的项目)
      port: 9999 # 执行器自己启动内嵌 Netty 服务器暴露的端口(默认9999，供后台请求你。如果你同机器跑多个应用必须改它防止端口冲突)
      logpath: /data/applogs/xxl-job/jobhandler # 在你本地保存详细日志流水的磁盘路径
      logretentiondays: 30 # 日志保存天数
    accessToken: default_token # 如果 admin 那边开启了安全令牌验证，这里必须要一致才能通信
```

### 3.3 注入核心配置类 XxlJobConfig
我们需要写一个常规的 `@Configuration` 配置类，把上面 YAML 里存活的信息组装成一个核心 Bean `XxlJobSpringExecutor` 扔给 Spring。代码几乎完全固定不变。

```java
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class XxlJobConfig {

    @Value("${xxl.job.admin.address}")
    private String adminAddresses;

    @Value("${xxl.job.accessToken}")
    private String accessToken;

    @Value("${xxl.job.executor.appname}")
    private String appname;

    @Value("${xxl.job.executor.port}")
    private int port;

    @Value("${xxl.job.executor.logpath}")
    private String logPath;

    @Value("${xxl.job.executor.logretentiondays}")
    private int logRetentionDays;

    @Bean
    public XxlJobSpringExecutor xxlJobExecutor() {
        log.info(">>>>>>>>>>> xxl-job config init.");
        XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
        xxlJobSpringExecutor.setAdminAddresses(adminAddresses);
        xxlJobSpringExecutor.setAppname(appname);
        xxlJobSpringExecutor.setPort(port);
        xxlJobSpringExecutor.setAccessToken(accessToken);
        xxlJobSpringExecutor.setLogPath(logPath);
        xxlJobSpringExecutor.setLogRetentionDays(logRetentionDays);
        return xxlJobSpringExecutor;
    }
}
```

### 3.4 编写业务定时任务 (极简极致体验)
如果你受够了 Quartz 写各种类然后组装的过程，在 XXL-JOB 里写任务简直就是天堂！
你完全不需要去理解啥叫 JobDetail、啥叫 Trigger，只需在一级甚至现有方法上方加一个 `@XxlJob` 注解：

```java
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SampleXxlJob {

    /**
     * 简单的每日数据对账任务示例
     */
    @XxlJob("dailyCheckJob") // 这里填的字符串就是这双手的名字，等会要求你在网页上严丝合缝地填入这串字母！
    public void executeDailyCheck() throws Exception {
        // 1. 获取调度中心(运营人员)在网页上下发的动态实参
        String param = XxlJobHelper.getJobParam();
        log.info("开始干活！接收到来自大脑的参数: {}", param);

        // 2. 这里就是您原汁原味的、连着 MyBatis 尽情飞舞的业务代码
        System.out.println("====== 正在拼命执行对账清单 ======");

        // 3. 【神级 API】：将打印的日志实时传输到后台管理系统的网页上，让人通过浏览器观摩进度！
        XxlJobHelper.log("正在处理第一批用户流水... 完成 10%");
        
        // 特别注意：只要方法正常无异常走完，在管理端就判定为“干活成功”！
        // 如果中间 throw 出了任意 Exception 抛了出去，框架会自动在网页上点亮红灯判定为“失败”！
    }
}
```

---

## 4. 第三步：在网页 Web 控制台进行神奇的配对！

现在，在您的 IDEA 里正常**启动您的 Spring Boot 业务系统**。
同时登录到之前那准备好的 `http://localhost:8080/xxl-job-admin` 后台。

### A. 登记执行器 (告诉大脑你叫啥名字)
- 点击左侧主菜单【执行器管理】 -> 【新增】。
- `AppName`：必须一字不落地填写刚才在 YML 里写的 `my-business-executor`。
- `名称`：随意取，给运营人员看的中文字“财务清算微服务集群”。
- 注册方式选【自动注册】即可！保存后等待大约 10~15 秒，您的这台电脑的 IP 和 9999 端口就会在这个页面上变成绿灯亮起，这就是伟大的**“自动发现”**机制。

### B. 新增调度任务配置 (建立牵挂与策略下发)
- 点击左侧菜单【任务管理】 -> 选中刚刚出现的这个执行器 -> 点击右侧【新增】。
- `调度类型`：选 `CRON`，后面填个通用的跑频：`0/10 * * * * ?`。
- **`JobHandler*`**：这是核心！老老实实填上您在自己项目 Java 代码里写的 `@XxlJob` 里的字符串 `dailyCheckJob`。不能错字母。
- `路由策略`：如果你有 10 台集群服务器，你想怎么跑？你可以随便选择。比如想轮流跑，你可以选 `轮询` 或者 `第一个` 等等。保存。

### C. 开始爽（界面的大杀器功能全开）
- **常规运行**：在操作列点击【启动】，后台就开始默默自动轮询打您的接口了！
- **不打乱计划调试**：在操作列点击【执行一次】，它只是临时立刻测一波你的代码连通性，毫不干扰原来的 Cron 时间节奏！而且这里点开可以顺手填入传递的字符串参数（也就是代码里 `getJobParam()` 去接收的）。
- **查看进度**：在查询列表点击【调度日志】，不仅能看有没有报异常失败，直接点击最右侧绿色的【执行日志】大按钮，您代码中写的 `XxlJobHelper.log("完成 10%")` 那几句流水的汇报信息，立刻通过流的形式呈现在白色的网页黑框中，老板看了直呼内行！

---

## 5. 高级核心绝学杀招：分片广播（大规模海量数据跑批）

**思考场景痛点**：
你有 1 亿个用户的“生日贺卡发送任务”要求在今晚 8 点必须在 1 小时内全部处理完发清。
一台服务器肯定会严重延时并扛不住。于是公司大气地加到了 10 个服务器节点形成大集群。
如果这种场景是在老旧的纯内存框架下，是非常难做的资源切分工程大活儿，甚至会互相抢锁导致效率跌底。

但是在 XXL-JOB 下利用**分片广播策略**，1 个配置按钮外加 2 行代码就能做：

**1. 网页配置的改动：**
只需在上述管理页任务的【路由策略】下拉框里，更改为 `分片广播`。

**2. 代码逻辑的微调升维：**
当时间到达今晚 8 点整时，**大脑会像核爆一样，无差别同时呼叫您的所有 10 台服务器节点发指令**。
您在业务代码里要做的事，就是拿到自己的身份标签去取属于自己那部分的数据即可：

```java
@XxlJob("batchBirthdayJob")
public void shardingJobHandler() throws Exception {
    
    // 拿到大脑赐予你的【全局分片信息】（极简又强大的设计核心！）
    int shardIndex = XxlJobHelper.getShardIndex(); // 身份问题：我是这 10 个干活的人里的第几个老六？(从 0 到 9 编号)
    int shardTotal = XxlJobHelper.getShardTotal(); // 大局观：总共有几台机器跟我一起同时下场干活？(等于 10 台)

    XxlJobHelper.log("收到广播任务，我是全服第 {} 号干将，总共有 {} 台机器群殴", shardIndex, shardTotal);

    // 【数据库经典 SQL 的王牌套路】：利用 取模查询法，把那 1 亿个不可描述的并发记录完美切开，一分钱都不起互斥冲突地分给这 10 个人！
    // Mapper 层对应的 XML SQL 比如这样写：SELECT * FROM user WHERE id % #{shardTotal} = #{shardIndex}
    // 大白话：编号 0 号机器只捞 id 结尾是 0 的去处理；3 号机器只负责捞去 id 结尾为 3 的数据去处理！
    List<User> myTaskSlice = userMapper.selectIdByMod(shardTotal, shardIndex);
    
    // 每台从茫茫亿条中毫无阻塞地各拿走只属于自己的那 1000 万个 User 数据，在各自的封闭池中畅快运算，性能和处理效率直接呈几何级原地飞起。
    for (User u : myTaskSlice) {
        // sendSms(u);
    }
}
```
**总结**：正是这最后这一手震撼且易用到底的**“数据分片广播”**降级操作，和**“全可视化运营报警管理”**功能，彻底让 XXL-JOB 在国内的分布式任务调度圈子里打爆各路好手，统一了几大长线的业务生态！
