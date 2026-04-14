package com.lin.config;

import com.lin.listener.GlobalJobResultListener;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.matchers.EverythingMatcher;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

/**
 *
 * @author linsz
 * @version v1.0
 * @date 2026/4/14 19:35
 */
@Configuration
@Slf4j
public class QuartzListenerConfig {

    @Autowired
    private GlobalJobResultListener globalJobResultListener;

    @Autowired
    private Scheduler scheduler;

    @PostConstruct
    public void init() throws SchedulerException {
        scheduler.getListenerManager()
//                .addJobListener(globalJobResultListener, GroupMatcher.anyJobGroup());
                .addJobListener(globalJobResultListener, EverythingMatcher.allJobs());
    }
}
