package com.triasoft.socialconnect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@ConfigurationPropertiesScan
public class SocialConnectApplication {
    public static void main(String[] args) {
        SpringApplication.run(SocialConnectApplication.class, args);
    }
}
