package io.zeitmaschine;

import static java.time.temporal.ChronoUnit.SECONDS;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import io.zeitmaschine.index.Indexer;
import io.zeitmaschine.index.IndexerHealthIndicator;
import io.zeitmaschine.s3.MinioHealthIndicator;
import io.zeitmaschine.s3.S3Repository;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Initializes resources that the application relies on to operate, like the elastic index, minio buckets.
 *
 * These resources should eventually be created by the user, or already exist upon start.
 */
@Component
public class ServiceInitializer {

    private final static Logger LOG = LoggerFactory.getLogger(ServiceInitializer.class.getName());
    private S3Repository repository;
    private Indexer indexer;
    private IndexerHealthIndicator indexerHealthIndicator;
    private MinioHealthIndicator minioHealthIndicator;

    @Autowired
    public ServiceInitializer(IndexerHealthIndicator indexerHealthIndicator, MinioHealthIndicator minioHealthIndicator, S3Repository repository, Indexer indexer) {
        this.indexerHealthIndicator = indexerHealthIndicator;
        this.minioHealthIndicator = minioHealthIndicator;
        this.repository = repository;
        this.indexer = indexer;
    }

    @EventListener
    public void onApplicationEvent(ContextRefreshedEvent event) {

        Mono<Health> minioLiveness = Mono.defer(() -> minioHealthIndicator.health());
        Mono<Health> elasticLiveness = Mono.defer(() -> indexerHealthIndicator.health());

        minioLiveness.retryWhen(Retry.fixedDelay(4, Duration.of(3, SECONDS)))
                .doOnSuccess(state -> repository.initBucket())
                .block();

        elasticLiveness.retryWhen(Retry.fixedDelay(4, Duration.of(3, SECONDS)))
                .doOnSuccess(state -> indexer.initIndex())
                .block();
    }
}