package io.zeitmaschine.index;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

/**
 *  The identifier for a given HealthIndicator is the name of the bean without the HealthIndicator suffix, if it exists.
 *  https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#writing-custom-healthindicators
 */
@Component
public class IndexerHealthIndicator implements ReactiveHealthIndicator {

    private final WebClient webClient;

    @Autowired
    public IndexerHealthIndicator(IndexerConfig config) {
        this.webClient = WebClient
                .builder()
                .baseUrl(config.getHost())
                .build();
    }

    @Override
    public Mono<Health> health() {
        return webClient.get()
                .uri("_cat/health")
                .retrieve()
                .toBodilessEntity()
                .map(response -> Health.up().build())
                // do not error signal here. Would be correcter tho.. IMO
                .onErrorResume(throwable -> Mono.just(Health.down().withException(throwable).build()));
    }
}
