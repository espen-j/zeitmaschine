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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;

import io.minio.BucketExistsArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
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
public class ImagingIntegrationTest {

    private static final String TEST_BUCKET = "test-bucket";
    private static final String TEST_IMAGE_NAME = "IMG_20181001_185137.jpg";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private S3Config config;

    private MinioClient minioClient;

    @BeforeEach
    void setUp() throws IOException, InvalidKeyException, InvalidResponseException, InsufficientDataException, NoSuchAlgorithmException, ServerException, InternalException, XmlParserException, InvalidBucketNameException, ErrorResponseException, RegionConflictException {
        this.minioClient = MinioClient.builder()
                .endpoint(config.getHost())
                .credentials(config.getAccess().getKey(), config.getAccess().getSecret()).build();

        Resource image = new ClassPathResource("images/IMG_20181001_185137.jpg");

        minioClient.makeBucket(MakeBucketArgs.builder().bucket(TEST_BUCKET).build());
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(TEST_BUCKET)
                .object(TEST_IMAGE_NAME)
                .stream(image.getInputStream(), image.contentLength(), -1)
                .contentType(MediaType.IMAGE_JPEG_VALUE)
                .build());
    }

    @AfterEach
    void tearDown() throws IOException, InvalidKeyException, InvalidResponseException, InsufficientDataException, NoSuchAlgorithmException, ServerException, InternalException, XmlParserException, InvalidBucketNameException, ErrorResponseException {
        minioClient.listObjects(ListObjectsArgs.builder()
                .bucket(TEST_BUCKET)
                .build())
                .forEach(obj -> {
                    try {
                        minioClient.removeObject(RemoveObjectArgs.builder()
                                .bucket(TEST_BUCKET)
                                .object(obj.get().objectName())
                                .build());
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to delete object.", e);
                    }
                });
        minioClient.removeBucket(RemoveBucketArgs.builder().bucket(TEST_BUCKET).build());
    }

    @Test
    @WithMockUser
    void name() {
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
}
