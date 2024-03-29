package io.zeitmaschine.s3;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import io.zeitmaschine.TestImagesProvider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;

@Testcontainers
public class MinioIT {

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
    private S3Repository s3Repository;

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

        s3Repository = new MinioRepository(config);

        // TODO: Write helper methods, see MetaDataMigratortest as well
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

        BucketHealthIndicator bucketHealthIndicator = new BucketHealthIndicator(s3Repository);
        Mono<Health> bucketsReady = Mono.defer(() -> bucketHealthIndicator.health());
        bucketsReady
                // healthIndicators return a Health object, we need error for retry
                .flatMap(health -> Status.UP.equals(health.getStatus()) ? Mono.just(health) : Mono.error(new RuntimeException("Bucket not ready")))
                .retryWhen(Retry.fixedDelay(5, Duration.of(3, SECONDS)))
                .doOnSuccess(health -> System.out.println("Buckets ready."))
                .doOnError(throwable -> System.out.println("Buckets not ready. Exiting"))
                .subscribe();
    }

    @ParameterizedTest
    @ArgumentsSource(TestImagesProvider.class)
    public void putAndGet(Resource image) {

        // GIVEN
        String filename = image.getFilename();
        s3Repository.put(config.getBucket(), filename, image, MediaType.IMAGE_JPEG_VALUE);

        // WHEN - THEN
        StepVerifier.create(s3Repository.get(config.getBucket(), filename))
                .expectNextMatches(entry -> entry.key().equals(filename))
                .verifyComplete();
    }

    @Test
    public void putAndGetPrefixed() {

        // GIVEN
        String prefix = "test-prefix/";
        String[] images = {"IMG_20161208_024708.jpg", "IMG_20180614_214734.jpg", "IMG_20181001_185137.jpg"};

        // prefixed
        Stream.of(images)
                .map(image -> new ClassPathResource("images/" + image))
                .forEach(resource -> s3Repository.put(config.getBucket(), prefix + resource.getFilename(), resource, MediaType.IMAGE_JPEG_VALUE));

        String nonPrefixedName = "IMG_20161208_024708.jpg";
        Resource nonPrefixed = new ClassPathResource("images/" + nonPrefixedName);
        s3Repository.put(config.getBucket(), nonPrefixed.getFilename(), nonPrefixed, MediaType.IMAGE_JPEG_VALUE);

        // WHEN - THEN
        StepVerifier.create(s3Repository.get(prefix))
                .expectNextMatches(entry -> entry.key().equals(prefix + "IMG_20161208_024708.jpg"))
                .expectNextMatches(entry -> entry.key().equals(prefix + "IMG_20180614_214734.jpg"))
                .expectNextMatches(entry -> entry.key().equals(prefix + "IMG_20181001_185137.jpg"))
                .verifyComplete();

        StepVerifier.create(s3Repository.get(config.getBucket(), nonPrefixedName))
                .expectNextMatches(entry -> entry.key().equals(nonPrefixedName))
                .verifyComplete();
    }

    @Test
    public void getAll() {

        // GIVEN
        byte[] bytes = new byte[1024*1000];
        new Random().nextBytes(bytes);
        int elements = 50;
        Flux.range(0, elements)
                .map(count -> S3Entry.builder()
                        .key(String.valueOf(count))
                        .contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                        .size(bytes.length)
                        .resourceSupplier(() -> new ByteArrayResource(bytes))
                        .build())
                .log()
                .subscribe(entry -> s3Repository.put(config.getBucket(), entry.key(), entry.resourceSupplier().get(), MediaType.APPLICATION_OCTET_STREAM_VALUE));

        // WHEN - THEN
        List<Integer> expected = IntStream.range(0, elements).boxed().collect(Collectors.toList());
        StepVerifier.create(s3Repository.get(""))
                .thenConsumeWhile(entry -> expected.remove(Integer.valueOf(entry.key())))
                .verifyComplete();

        assertTrue(expected.isEmpty());

    }

    @Test
    void metadata() {
        // GIVEN
        ClassPathResource image = new ClassPathResource("images/PXL_20220202_160830986.MP.jpg");

        S3Repository wrapped = MetaDataProcessingRepository.wrap(s3Repository);
        wrapped.put(config.getBucket(), image.getFilename(), image, MediaType.IMAGE_JPEG_VALUE);

        // WHEN - THEN
        StepVerifier.create(wrapped.get(config.getBucket(), image.getFilename()))
                .assertNext(entry -> {
                    assertNotNull(entry.location().lat());
                    assertNotNull(entry.location().lon());
                    assertNotNull(entry.created());
                    assertThat(entry.contentType(), is(MediaType.IMAGE_JPEG_VALUE));
                })
                .verifyComplete();
    }

    @Test
    void processedContentTypePersisted() {
        // GIVEN
        ClassPathResource image = new ClassPathResource("images/PXL_20220202_160830986.MP.jpg");

        // Set incorrect content type
        s3Repository.put(config.getBucket(), image.getFilename(), image, MediaType.APPLICATION_OCTET_STREAM_VALUE);

        StepVerifier.create(s3Repository.get(config.getBucket(), image.getFilename()))
                .assertNext(entry -> assertThat(entry.contentType(), is(MediaType.APPLICATION_OCTET_STREAM_VALUE)))
                .verifyComplete();

        // WHEN - Set correct content type
        s3Repository.metaData(image.getFilename(), Map.of(), MediaType.IMAGE_JPEG_VALUE);

        // THEN
        StepVerifier.create(s3Repository.get(config.getBucket(), image.getFilename()))
                .assertNext(entry -> assertThat(entry.contentType(), is(MediaType.IMAGE_JPEG_VALUE)))
                .verifyComplete();
    }
}


