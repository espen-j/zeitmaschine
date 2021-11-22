package io.zeitmaschine.s3;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import io.zeitmaschine.TestImagesProvider;
import reactor.test.StepVerifier;

@Testcontainers
public class MinioIT {

    private static final String MINIO_CONTAINER = "minio/minio:RELEASE.2021-11-05T09-16-26Z";
    private static final int MINIO_PORT = 9000;

    // Needs the host and port of the application: host.docker.internal:8080
    // Hence DEFINED_PORT needed for this test.
    // Chicken egg problem.
    @Container
    private static GenericContainer minioContainer = new GenericContainer(DockerImageName.parse(MINIO_CONTAINER))
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

        s3Repository = new S3Repository(config);

        s3Repository.initBucket();
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
}


