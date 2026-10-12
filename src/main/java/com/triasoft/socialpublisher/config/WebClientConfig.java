package com.triasoft.socialpublisher.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(30);

    /** Shared, bounded-timeout builder — each outbound client (Meta, Replicate, webhook dispatch) sets its own base URL. */
    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
                .responseTimeout(RESPONSE_TIMEOUT);
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
