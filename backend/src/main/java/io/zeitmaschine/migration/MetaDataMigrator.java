package io.zeitmaschine.migration;

import static io.zeitmaschine.s3.Processor.META_VERSION;
import static java.time.temporal.ChronoUnit.SECONDS;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import io.zeitmaschine.s3.BucketHealthIndicator;
import io.zeitmaschine.s3.S3Repository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@Component
@Profile("migrate")
public class MetaDataMigrator {

    private final static Logger log = LoggerFactory.getLogger(MetaDataMigrator.class.getName());

    private final S3Repository repository;
    private final BucketHealthIndicator bucketHealthIndicator;

    @Autowired
    public MetaDataMigrator(S3Repository repository, BucketHealthIndicator bucketHealthIndicator) {
        this.repository = repository;
        this.bucketHealthIndicator = bucketHealthIndicator;
    }

    @EventListener
    public void onEvent(ContextRefreshedEvent event) {

        Mono<Health> bucketsReady = Mono.defer(() -> bucketHealthIndicator.health());

        bucketsReady
                // healthIndicators return a Health object, we need error for retry
                .flatMap(health -> Status.UP.equals(health.getStatus()) ? Mono.just(health) : Mono.error(new RuntimeException("Bucket not ready")))
                .retryWhen(Retry.fixedDelay(5, Duration.of(3, SECONDS)))
                .doOnSuccess(health -> migrate())
                .subscribe();
    }

    void migrate() {
        repository.get("2021-pixel5")
                .publishOn(Schedulers.single())
                .subscribe(s3Entry -> {
                    if (s3Entry.metaData().containsKey(META_VERSION)) {
                        log.info("Deleting meta data for '{}'", s3Entry.key());
                        repository.metaData(s3Entry.key(), Map.of(), s3Entry.contentType());
                    }
                });
    }
}
