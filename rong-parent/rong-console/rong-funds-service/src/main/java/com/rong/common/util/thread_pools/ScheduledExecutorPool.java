package com.rong.common.util.thread_pools;

import com.rong.common.util.CommonUtil;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ScheduledFuture;

public class ScheduledExecutorPool{
    private static final Map<String,ScheduledFuture> futures = new HashMap<>();
    private static final Map<String,WorkTask> works = new HashMap<>();
    private static volatile boolean isStop = false;
    private static ScheduledExecutorPool instance = new ScheduledExecutorPool();
    public static ScheduledExecutorPool getInstance(){return instance;}
    private ScheduledExecutorPool(){}

    public void addWorkTask(WorkTask task){
        if(isStop){
            return;
        }
        works.put(task.getWorkId(),task);
    }
    public void removeWorkTask(String workId){
        ScheduledFuture future = futures.get(workId);
        if(CommonUtil.isNotNull(future)){
            future.cancel(true);
            futures.remove(workId);
            works.remove(workId);
        }
    }
    public void stopAllWork(){
        isStop = true;
        for(Map.Entry<String,ScheduledFuture> e:futures.entrySet()){
            e.getValue().cancel(true);
        }
        futures.clear();
        works.clear();
    }
    ThreadPoolTaskScheduler threadPoolTaskScheduler;
    public void beginWorks(){
        if(isStop){
            return;
        }
        threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
        threadPoolTaskScheduler.setPoolSize(works.size());
        threadPoolTaskScheduler.setThreadNamePrefix("viy-scheduled-");
        threadPoolTaskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        threadPoolTaskScheduler.setAwaitTerminationSeconds(10);
        threadPoolTaskScheduler.initialize();
        long now = System.currentTimeMillis();
        for(Map.Entry<String,WorkTask> e:works.entrySet()){
            WorkTask task = e.getValue();
            ScheduledFuture future = threadPoolTaskScheduler.scheduleWithFixedDelay(task,new Date(now + task.getInitialDelay()),task.getDelayOfMillSeconds());
            futures.put(e.getKey(),future);
        }
    }
    @PreDestroy
    public void shutdown0(){
        stopAllWork();//????????????????????????????????????????????????????????????????????????????????????????????????????????????
        //????????????????????????????????????????????????????????????????????????????????????
        if(CommonUtil.isNotNull(threadPoolTaskScheduler)){
            threadPoolTaskScheduler.shutdown();
        }
    }
}
