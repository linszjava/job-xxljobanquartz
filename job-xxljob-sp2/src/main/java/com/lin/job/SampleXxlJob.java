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
                
            } else {
                XxlJobHelper.log("第 {} 片节点，[忽略] 数据 ID: {} (交给了其它节点)", shardIndex, id);
            }
        }
    }
}
