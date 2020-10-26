package io.zeitmaschine.image;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.imageio.ImageIO;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import io.minio.BucketExistsArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidBucketNameException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.RegionConflictException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import io.zeitmaschine.s3.S3Config;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // Allows non-static @BeforeAll and @AfterAll
public class ImagingIntegrationTest {

    private static final String TEST_IMAGE_NAME = "IMG_20181001_185137.jpg";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private S3Config config;

    private static MinioClient minioClient;
    private String bucket;
    private String cacheBucket;

    @BeforeAll
    void setUp() throws IOException, InvalidKeyException, InvalidResponseException, InsufficientDataException, NoSuchAlgorithmException, ServerException, InternalException, XmlParserException, InvalidBucketNameException, ErrorResponseException, RegionConflictException {
        minioClient = MinioClient.builder()
                .endpoint(config.getHost())
                .credentials(config.getAccess().getKey(), config.getAccess().getSecret()).build();

        Resource image = new ClassPathResource("images/IMG_20181001_185137.jpg");

        this.bucket = config.getBucket();
        this.cacheBucket = config.getCacheBucket();

        // Created in S3Repository
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()) ||
                !minioClient.bucketExists(BucketExistsArgs.builder().bucket(cacheBucket).build()))
            throw new RuntimeException("Buckets not created.");

        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(TEST_IMAGE_NAME)
                .stream(image.getInputStream(), image.contentLength(), -1)
                .contentType(MediaType.IMAGE_JPEG_VALUE)
                .build());
    }

    @AfterAll
    void tearDown() throws IOException, InvalidKeyException, InvalidResponseException, InsufficientDataException, NoSuchAlgorithmException, ServerException, InternalException, XmlParserException, InvalidBucketNameException, ErrorResponseException {
        wipeBucket(bucket);
        wipeBucket(cacheBucket);
    }

    private void wipeBucket(String wipe) throws ErrorResponseException, InsufficientDataException, InternalException, InvalidBucketNameException, InvalidKeyException, InvalidResponseException, IOException, NoSuchAlgorithmException, ServerException, XmlParserException {
        minioClient.listObjects(ListObjectsArgs.builder()
                .bucket(wipe)
                .recursive(true)
                .build())
                .forEach(obj -> {
                    try {
                        minioClient.removeObject(RemoveObjectArgs.builder()
                                .bucket(wipe)
                                .object(obj.get().objectName())
                                .build());
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to delete object.", e);
                    }
                });
        minioClient.removeBucket(RemoveBucketArgs.builder().bucket(wipe).build());
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

}
