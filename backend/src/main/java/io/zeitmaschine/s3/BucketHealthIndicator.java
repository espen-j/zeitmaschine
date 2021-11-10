package io.zeitmaschine.s3;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

@Component
public class BucketHealthIndicator implements ReactiveHealthIndicator {

    private final S3Repository repository;

    @Autowired
    public BucketHealthIndicator(S3Repository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<Health> health() {
        return repository.health() ? Mono.just(Health.up().build()) : Mono.just(Health.down().build());
    }
}
