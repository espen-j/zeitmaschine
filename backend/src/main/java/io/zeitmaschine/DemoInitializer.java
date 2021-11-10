package io.zeitmaschine;

import static java.time.temporal.ChronoUnit.SECONDS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import io.zeitmaschine.s3.BucketHealthIndicator;
import io.zeitmaschine.s3.S3Config;
import io.zeitmaschine.s3.S3Repository;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Component
@Profile("demo")
public class DemoInitializer {

    private final String bucket;
    private BucketHealthIndicator bucketHealthIndicator;
    private final S3Repository repository;

    @Value("${demo.dir}")
    Path demoDir;

    @Autowired
    public DemoInitializer(S3Repository repository, S3Config config, BucketHealthIndicator bucketHealthIndicator) {
        this.repository = repository;
        this.bucket = config.getBucket();
        this.bucketHealthIndicator = bucketHealthIndicator;
    }

    @EventListener
    public void bootstrapDemo(ContextRefreshedEvent event) {

        Mono<Health> bucketsReady = Mono.defer(() -> bucketHealthIndicator.health());

        bucketsReady
                // healthIndicators return a Health object, we need error for retry
                .flatMap(health -> Status.UP.equals(health.getStatus()) ? Mono.just(health) : Mono.error(new RuntimeException("Bucket not ready")))
                .retryWhen(Retry.fixedDelay(5, Duration.of(3, SECONDS)))
                .doOnSuccess(health -> bootstrap())
                .subscribe();
    }

    void bootstrap() {
        try (Stream<Path> paths = Files.walk(demoDir)) {
            paths
                    .filter(Files::isRegularFile)
                    .forEach(path -> repository.put(bucket, path.getFileName().toString(), new FileSystemResource(path), MediaType.IMAGE_JPEG_VALUE));
        } catch (IOException e) {
            throw new RuntimeException("Error initializing demo content.", e);
        }
    }

}
