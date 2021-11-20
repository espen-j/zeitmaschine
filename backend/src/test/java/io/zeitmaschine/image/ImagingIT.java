package io.zeitmaschine.image;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import javax.imageio.ImageIO;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import io.zeitmaschine.s3.S3Config;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = { ImagingIT.Initializer.class })
@Testcontainers
public class ImagingIT {

    private static final String TEST_IMAGE_NAME = "IMG_20181001_185137.jpg";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private S3Config config;

    private static MinioClient minioClient;
    private String bucket;
    private String cacheBucket;

    private static final String MINIO_CONTAINER = "minio/minio:RELEASE.2021-11-05T09-16-26Z";
    private static final int MINIO_PORT = 9000;

    // will be shared between test methods
    @Container
    private static GenericContainer minioContainer = new GenericContainer(DockerImageName.parse(MINIO_CONTAINER))
            .withEnv(Map.of(
                    "MINIO_ACCESS_KEY", "test",
                    "MINIO_SECRET_KEY", "testtest",
                    "MINIO_NOTIFY_WEBHOOK_ENABLE_zm", "on",
                    "MINIO_NOTIFY_WEBHOOK_ENDPOINT_zm", "http://zm-test:8080/s3/webhook"))
            .withCommand("server /data")
            .withExposedPorts(MINIO_PORT);


    private static final String ELASTICSEARCH_VERSION = "6.5.4";
    private static final String ELASTIC_CONTAINER = "docker.elastic.co/elasticsearch/elasticsearch";

    private static final Integer ELASTIC_PORT = 9200;

    // will be shared between test methods
    @Container
    private static GenericContainer elasticContainer = new ElasticsearchContainer(
            DockerImageName
                    .parse(ELASTIC_CONTAINER)
                    .withTag(ELASTICSEARCH_VERSION))
            .withEnv(Map.of(
                    "discovery.type", "single-node",
                    "http.cors.enabled", "true",
                    "http.cors.allow-origin", "*"
            ))
            .withExposedPorts(ELASTIC_PORT);

    private static final int IMAGINARY_PORT = 8088;

    // will be shared between test methods
    @Container
    private static GenericContainer imaginaryContainer = new GenericContainer(DockerImageName.parse("h2non/imaginary:1.2.4"))
            .withEnv("PORT", String.valueOf(IMAGINARY_PORT))
            .withExposedPorts(IMAGINARY_PORT);


    @BeforeEach
    void setUp() throws IOException, InvalidKeyException, InvalidResponseException, InsufficientDataException, NoSuchAlgorithmException, ServerException, InternalException, XmlParserException, ErrorResponseException {

        minioClient = MinioClient.builder()
                .endpoint(config.getHost())
                .credentials(config.getAccess().getKey(), config.getAccess().getSecret()).build();

        Resource image = new ClassPathResource("images/IMG_20181001_185137.jpg");

        this.bucket = config.getBucket();
        this.cacheBucket = config.getCacheBucket();

        // Created in S3Repository
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()) ||
                !minioClient.bucketExists(BucketExistsArgs.builder().bucket(cacheBucket).build())) {
            throw new RuntimeException("Buckets not created.");
        }

        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(TEST_IMAGE_NAME)
                .stream(image.getInputStream(), image.contentLength(), -1)
                .contentType(MediaType.IMAGE_JPEG_VALUE)
                .build());
    }

    /**
     * This test ensures that an authenticated user can call the image endpoint with a request for an image
     * operation. The passed image must exist in the s3 bucket, from where it is loaded and passed to the imaginary service.
     *
     * The expected result is a resized image.
     */
    @Test
    @WithMockUser
    void routedImageOperation() {
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("image/")
                        .path(String.valueOf(Dimension.SMALL))
                        .queryParam("name", TEST_IMAGE_NAME)
                        .build())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_JPEG_VALUE)
                .accept(MediaType.IMAGE_JPEG)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.IMAGE_JPEG)
                .expectBody(byte[].class)
                .consumeWith(response -> {
                    byte[] responseBody = response.getResponseBody();
                    try {
                        BufferedImage img = ImageIO.read(new ByteArrayInputStream(responseBody));
                        assertThat(img.getWidth(), CoreMatchers.is(Dimension.SMALL.getSize()));
                    } catch (IOException e) {
                        fail(e.getMessage());
                    }
                });
    }

    @Test
    @WithMockUser
    void nonExistingObject() {
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("image/")
                        .path(String.valueOf(Dimension.SMALL))
                        .queryParam("name", "DOES_NOT_EXIST.jpg")
                        .build())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_JPEG_VALUE)
                .accept(MediaType.IMAGE_JPEG)
                .exchange()
                //.expectStatus().is4xxClientError(); this is correcter.
                .expectStatus().is5xxServerError();
    }


    // https://www.baeldung.com/spring-boot-testcontainers-integration-test
    static class Initializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            TestPropertyValues.of(
                    "s3.host=" + "http://" + minioContainer.getHost() + ":" + minioContainer.getMappedPort(MINIO_PORT),
                    "elasticsearch.host=" + "http://" + elasticContainer.getHost() + ":" + elasticContainer.getMappedPort(ELASTIC_PORT),
                    "imaginary.host=" + "http://" + imaginaryContainer.getHost() + ":" + imaginaryContainer.getMappedPort(IMAGINARY_PORT)
            ).applyTo(configurableApplicationContext.getEnvironment());
        }
    }

}
