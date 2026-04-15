package com.lin.job;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 *
 * @author linsz
 * @version v1.0
 * @date 2026/4/15 14:11
 */
@Slf4j
@Component
public class SampleXxlJob {

    @XxlJob("sizeDailyWork")
    public void executeDailyWork() {
        String jobParam = XxlJobHelper.getJobParam();
        log.info("[SampleXxlJob] executeDailyWork jobParam: {}", jobParam);

        log.info("[SampleXxlJob] executeDailyWork 连接数据库 执行的一系列操作");

        XxlJobHelper.log("[SampleXxlJob] executeDailyWork 任务执行: {}%", 10);
    }

    /**
     * 分片广播任务示例
     */
    @XxlJob("shardingJobHandler")
    public void shardingJobHandler() {
        // 1. 获取分片参数
        int shardIndex = XxlJobHelper.getShardIndex(); // 当前机器的分片序号 (从0开始)
        int shardTotal = XxlJobHelper.getShardTotal(); // 集群中总共的机器数量

        XxlJobHelper.log("分片参数获取成功：当前分片序号(index) = {}, 总分片数(total) = {}", shardIndex, shardTotal);
        log.info("开始执行分片任务，当前机器分片序号: {}/{}", shardIndex, shardTotal);

        // 2. 模拟业务数据 (假设从数据库里面拿出了 10 条需要处理的数据)
        List<Integer> allDataIds = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

        // 3. 遍历数据，进行分片路由计算
        for (Integer id : allDataIds) {
            // 【核心分片逻辑】：如果这条数据的 ID 取模 总机器数，正好等于当前机器的序号，当前机器才动手处理
            if (id % shardTotal == shardIndex) {
                XxlJobHelper.log("第 {} 片节点，[命中] 分配处理数据 ID: {}", shardIndex, id);
                log.info("命中！当前机器(分片{})正在处理业务数据: ID={}", shardIndex, id);
                
                // --- 这里写你真正的业务处理逻辑 ---
                /**
                 * 在真实的业务代码中，我们依然使用取模的逻辑，但我们会把算式写进 SQL 里！让数据库帮我们把数据切好，每个节点只拿自己该拿的数据。
                 *
                 * 真实的业务代码看起来通常是这样的：
                 *
                 * java
                 * @XxlJob("shardingJobHandler")
                 * public void shardingJobHandler() {
                 *     int shardIndex = XxlJobHelper.getShardIndex();
                 *     int shardTotal = XxlJobHelper.getShardTotal();
                 *     // 每次只取 1000 条，分批处理，防止内存撑爆 (游标或分页)
                 *     int limit = 1000;
                 *     // 重点在这里！直接把分片参数传给 Mapper/DAO 层
                 *     List<Order> myOrders = orderMapper.findPendingOrdersBySharding(shardIndex, shardTotal, limit);
                 *     for (Order order : myOrders) {
                 *         // 直接处理，不需要再写 if (id % total == index) 了，因为查出来的全是我的！
                 *         processOrder(order);
                 *     }
                 * }
                 * 而你的 Mybatis Mapper (XML) 里的 SQL 是这样的：
                 *
                 * sql
                 * SELECT * FROM t_order
                 * WHERE status = 'PENDING'
                 *   -- 真正的精髓在这里：利用 MySQL 的 MOD 函数进行取模，相当于 Java 里的 %
                 *   AND MOD(id, #{shardTotal}) = #{shardIndex}
                 * LIMIT #{limit}
                 * 总结
                 * 业务思想：绝大数场景下，确实都是依靠主键（或 UserID 等业务键）取模来实现分片的，这种方式最均匀、最不会产生数据倾斜。
                 * 工程实现：千万不要拿到所有数据后在 Java 内存里 for 循环取模，而是要把**分片总数（shardTotal）和当前分片序号（shardIndex）**作为参数传递给 SQL，利用数据库的 MOD() 函数直接在底层完成数据的切分和过滤。
                 */
                
            } else {
                XxlJobHelper.log("第 {} 片节点，[忽略] 数据 ID: {} (交给了其它节点)", shardIndex, id);
            }
        }
    }
}
