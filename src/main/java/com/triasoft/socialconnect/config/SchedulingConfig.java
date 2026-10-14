package com.triasoft.socialconnect.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulingConfig {

    // Spring Boot's @EnableScheduling default is a single-threaded scheduler — fine for a handful
    // of cron jobs, but SocialJobPoller's three advance* methods each make blocking external HTTP
    // calls (Meta, Replicate) and shouldn't be able to stall one another. Sized at 4 so all three
    // can run concurrently with headroom.
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("social-poller-");
        scheduler.initialize();
        return scheduler;
    }
}
