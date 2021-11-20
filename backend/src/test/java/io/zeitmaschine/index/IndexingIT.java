package io.zeitmaschine.index;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.MinioException;
import io.zeitmaschine.s3.S3Config;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ContextConfiguration(initializers = { IndexingIT.Initializer.class })
@Testcontainers
@AutoConfigureWebTestClient
public class IndexingIT {

    private static final String TEST_IMAGE_NAME = "IMG_20181001_185137.jpg";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private S3Config config;

    private static MinioClient minioClient;
    private String bucket;

    private static final String MINIO_CONTAINER = "minio/minio:RELEASE.2020-10-09T22-55-05Z";
    private static final int MINIO_PORT = 9000;

    // Needs the host and port of the application: host.docker.internal:8080
    // Hence DEFINED_PORT needed for this test.
    // Chicken egg problem.
    @Container
    private static GenericContainer minioContainer = new GenericContainer(DockerImageName.parse(MINIO_CONTAINER))
            .withEnv(Map.of(
                    "MINIO_ACCESS_KEY", "test",
                    "MINIO_SECRET_KEY", "testtest",
                    "MINIO_NOTIFY_WEBHOOK_ENABLE_zm", "on",
                    "MINIO_NOTIFY_WEBHOOK_ENDPOINT_zm", "http://" + getDockerHost() + ":8080/index/webhook"))
            .withCommand("server /data")
            .withExposedPorts(MINIO_PORT);

    private static String getDockerHost() {
        String os = System.getProperty("os.name", "generic");
        // Works for macOs and should work for windows
        // https://stackoverflow.com/a/40789612
        return os.contains("nux") ? "172.17.0.1" : "host.docker.internal";
    }


    private static final String ELASTICSEARCH_VERSION = "7.14.2";
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
    void setUp() {
        minioClient = MinioClient.builder()
                .endpoint(config.getHost())
                .credentials(config.getAccess().getKey(), config.getAccess().getSecret()).build();

        this.bucket = config.getBucket();

    }

    /**
     * Tests minio's webhook configuration that notifies our index endpoint and creates a new
     * image resource ein elastic search.
     */
    @Test
    @WithMockUser
    void uploadIndexed() throws IOException, MinioException, NoSuchAlgorithmException, InvalidKeyException, JSONException, InterruptedException {
        Resource image = new ClassPathResource("images/IMG_20181001_185137.jpg");
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(TEST_IMAGE_NAME)
                .stream(image.getInputStream(), image.contentLength(), -1)
                .contentType(MediaType.IMAGE_JPEG_VALUE)
                .build());

        // index might not have been initialized, wait a bit..
        Thread.sleep(5000);

        String jsonString = new JSONObject()
                .put("from", 0)
                .put("size", "3")
                .toString();

        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/zeitmaschine/image/_search")
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(jsonString))
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE) // elastic returns deprecated content type still
                .expectBody()
                .jsonPath("$.hits.hits").isArray()
                .jsonPath("$.hits.hits[0]._source.name").isEqualTo("IMG_20181001_185137.jpg");
        // body example: {"took":90,"timed_out":false,"_shards":{"total":5,"successful":5,"skipped":0,"failed":0},"hits":{"total":1,"max_score":1.0,"hits":[{"_index":"zeitmaschine","_type":"image","_id":"_QwYxHYBXRNBi9tRsreb","_score":1.0,"_source":{"name":"404_resized.jpg","created":null,"location":null}}]}}


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
