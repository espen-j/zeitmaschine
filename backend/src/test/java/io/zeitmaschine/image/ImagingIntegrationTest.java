package io.zeitmaschine.image;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ImagingIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @WithMockUser
    void name() {
        String imageName = "IMG_20161210_002952.jpg"; // must be in s3

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("image/")
                        .path(String.valueOf(Dimension.SMALL))
                        .queryParam("name", imageName)
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
