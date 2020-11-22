package io.zeitmaschine.s3;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

@Component
public class MinioHealthIndicator implements ReactiveHealthIndicator {

    private final WebClient webClient;

    @Autowired
    public MinioHealthIndicator(S3Config config) {
        this.webClient = WebClient
                .builder()
                .baseUrl(config.getHost())
                .build();
    }

    @Override
    public Mono<Health> health() {
        return webClient.get()
                .uri("minio/health/live")
                .exchangeToMono(clientResponse -> Mono.just(Health.up().build()))
                .onErrorResume(throwable -> Mono.just(Health.down().withException(throwable).build()));
    }
}
