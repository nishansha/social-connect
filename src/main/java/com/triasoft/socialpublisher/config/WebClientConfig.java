package com.triasoft.socialpublisher.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Prototype-scoped — this is load-bearing, not cosmetic. {@link WebClient.Builder}'s mutator
     * methods ({@code .baseUrl(...)}, {@code .defaultHeader(...)}) mutate the instance in place and
     * return {@code this}; they don't copy. A singleton bean here means every consumer that calls
     * {@code .baseUrl(...)}/{@code .defaultHeader(...)} (see {@code ReplicateVideoGenClient},
     * {@code MetaOAuthClient}, {@code InstagramGraphClient}) permanently contaminates the SAME
     * shared builder for every other consumer constructed afterward — e.g. Replicate's API key
     * ended up as a default {@code Authorization} header on {@code MinioObjectStorageService}'s
     * plain downloader, which MinIO then rejected ("request has multiple authentication types")
     * since presigned URLs carry their own query-string signature. Prototype scope restores what
     * Spring Boot's own auto-configured {@code WebClient.Builder} bean does by default, and is
     * what makes the "each outbound client sets its own base URL" comment below actually true —
     * without it, only the first-constructed consumer's customization actually "sticks" cleanly.
     */
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
                .responseTimeout(RESPONSE_TIMEOUT);
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
