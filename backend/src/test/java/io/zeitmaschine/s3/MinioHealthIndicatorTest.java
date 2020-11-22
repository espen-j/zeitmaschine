package io.zeitmaschine.s3;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import io.zeitmaschine.index.IndexerConfig;
import io.zeitmaschine.index.IndexerHealthIndicator;
import reactor.test.StepVerifier;

@Testcontainers
class MinioHealthIndicatorTest {

    private static final String MINIO_CONTAINER = "minio/minio:RELEASE.2020-10-09T22-55-05Z";
    private static final int MINIO_PORT = 9000;

    // will be shared between test methods
    @Container
    private static GenericContainer minioContainer = new GenericContainer(DockerImageName.parse(MINIO_CONTAINER))
            .withEnv(Map.of(
                    "MINIO_ACCESS_KEY", "test",
                    "MINIO_SECRET_KEY", "testtest"))
            .withCommand("server /data")
            .withExposedPorts(MINIO_PORT);

    private String minioHost;

    @BeforeEach
    private void beforeAll() {
        this.minioHost = "http://" + minioContainer.getHost() + ":" + minioContainer.getMappedPort(MINIO_PORT);
    }

    @Test
    void health() {
        IndexerConfig config = new IndexerConfig();
        config.setHost(minioHost);
        StepVerifier.create(new IndexerHealthIndicator(config).health())
                .expectNext(Health.up().build())
                .expectComplete();
    }
}