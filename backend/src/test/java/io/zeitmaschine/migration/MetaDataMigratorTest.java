package io.zeitmaschine.migration;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import io.zeitmaschine.s3.BucketHealthIndicator;
import io.zeitmaschine.s3.MinioHealthIndicator;
import io.zeitmaschine.s3.MinioRepository;
import io.zeitmaschine.s3.Processor;
import io.zeitmaschine.s3.S3Config;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;

@Testcontainers
class MetaDataMigratorTest {

    private static final String MINIO_CONTAINER = "minio/minio:RELEASE.2021-11-05T09-16-26Z";
    private static final int MINIO_PORT = 9000;

    // Needs the host and port of the application: host.docker.internal:8080
    // Hence DEFINED_PORT needed for this test.
    // Chicken egg problem.
    @Container
    private GenericContainer minioContainer = new GenericContainer(DockerImageName.parse(MINIO_CONTAINER))
            .withEnv(Map.of(
                    "MINIO_ACCESS_KEY", "test",
                    "MINIO_SECRET_KEY", "testtest"))
            .withCommand("server /data")
            .withExposedPorts(MINIO_PORT);
    private S3Config config;
    private MinioRepository s3Repository;
    private BucketHealthIndicator bucketHealthIndicator;

    @BeforeEach
    void setUp() {

        this.config = new S3Config();
        config.setBucket("media");
        config.setCacheBucket("media-cache");
        S3Config.Access access = new S3Config.Access();
        access.setKey("test");
        access.setSecret("testtest");
        config.setAccess(access);
        config.setWebhook(false);
        config.setHost("http://" + minioContainer.getHost() + ":" + minioContainer.getMappedPort(MINIO_PORT));

        this.s3Repository = new MinioRepository(config);

        this.bucketHealthIndicator = new BucketHealthIndicator(s3Repository);

        // TODO: Write helper methods, see MinioIT as well
        MinioHealthIndicator minoHealthIndicator = new MinioHealthIndicator(config);
        Mono<Health> minioReady = Mono.defer(() -> minoHealthIndicator.health());
        minioReady
                // healthIndicators return a Health object, we need error for retry
                .flatMap(health -> Status.UP.equals(health.getStatus()) ? Mono.just(health) : Mono.error(new RuntimeException("Bucket not ready")))
                .retryWhen(Retry.fixedDelay(5, Duration.of(3, SECONDS)))
                .doOnSuccess(health -> {
                    System.out.println("Minio ready.");
                    s3Repository.initBucket(); // This one is important!
                })
                .doOnError(throwable -> System.out.println("Minio not ready. Exiting"))
                .subscribe();

        this.bucketHealthIndicator = new BucketHealthIndicator(s3Repository);
        Mono<Health> bucketsReady = Mono.defer(() -> bucketHealthIndicator.health());
        bucketsReady
                // healthIndicators return a Health object, we need error for retry
                .flatMap(health -> Status.UP.equals(health.getStatus()) ? Mono.just(health) : Mono.error(new RuntimeException("Bucket not ready")))
                .retryWhen(Retry.fixedDelay(5, Duration.of(3, SECONDS)))
                .doOnSuccess(health -> System.out.println("Buckets ready."))
                .doOnError(throwable -> System.out.println("Buckets not ready. Exiting"))
                .subscribe();
    }

    @Test
    void name() throws InterruptedException {

        // GIVEN
        String[] images = {"IMG_20161208_024708.jpg", "IMG_20180614_214734.jpg", "IMG_20181001_185137.jpg"};
        String prefix = "2021-pixel5/";

        Stream.of(images)
                .map(image -> new ClassPathResource("images/" + image))
                .forEach(resource -> {
                    String key = prefix + resource.getFilename();
                    s3Repository.put(config.getBucket(), key, resource, MediaType.IMAGE_JPEG_VALUE);

                    // Update with crap meta-data
                    Map<String, String> metaData = Map.of(
                            Processor.META_VERSION, "1,1,1,1",
                            "Content-Type", "image/jpeg,image/jpeg,image/jpeg,image/jpeg",
                            Processor.META_CREATION_DATE, "1611175311488,1611175311488,1611175311488,1611175311488",
                            Processor.META_LOCATION_LAT, "47.55693055555555,47.55693055555555,47.55693055555555,47.55693055555555",
                            Processor.META_LOCATION_LON, "47.55693055555555,47.55693055555555,47.55693055555555,47.55693055555555"
                    );
                    s3Repository.metaData(key, metaData, MediaType.IMAGE_JPEG_VALUE);
                });

        bucketHealthIndicator = new BucketHealthIndicator(s3Repository);
        MetaDataMigrator migrator = new MetaDataMigrator(s3Repository, bucketHealthIndicator);

        Thread.sleep(5000);

        StepVerifier.create(s3Repository.get(config.getBucket(), prefix + "IMG_20161208_024708.jpg"))
                .assertNext(entry -> {
                    assertNotNull(entry.metaData().get(Processor.META_VERSION));
                    assertNotNull(entry.metaData().get(Processor.META_CREATION_DATE));
                    assertNotNull(entry.metaData().get(Processor.META_LOCATION_LON));
                    assertNotNull(entry.metaData().get(Processor.META_LOCATION_LAT));
                })
                .verifyComplete();

        StepVerifier.create(s3Repository.get(prefix))
                .assertNext(entry -> {
                    assertNotNull(entry.metaData().get(Processor.META_VERSION));
                    assertNotNull(entry.metaData().get(Processor.META_CREATION_DATE));
                    assertNotNull(entry.metaData().get(Processor.META_LOCATION_LON));
                    assertNotNull(entry.metaData().get(Processor.META_LOCATION_LAT));
                })
                .expectNextCount(2)
                .verifyComplete();

        // WHEN
        migrator.onEvent(mock(ContextRefreshedEvent.class));
        Thread.sleep(5000);

        // THEN
        StepVerifier.create(s3Repository.get(prefix))
                .assertNext(entry -> {
                    assertNull(entry.metaData().get(Processor.META_VERSION));
                    assertNull(entry.metaData().get(Processor.META_CREATION_DATE));
                    assertNull(entry.metaData().get("Content-Type"));
                    assertNull(entry.metaData().get(Processor.META_LOCATION_LON));
                    assertNull(entry.metaData().get(Processor.META_LOCATION_LAT));
                })
                .expectNextCount(2)
                .verifyComplete();
    }
}