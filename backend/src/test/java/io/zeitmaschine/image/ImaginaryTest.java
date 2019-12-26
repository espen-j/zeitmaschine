package io.zeitmaschine.image;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;

public class ImaginaryTest {

    private final static Logger LOG = LoggerFactory.getLogger(ImaginaryTest.class.getName());

    private static final String[] images = {"IMG_20161208_024708.jpg", "IMG_20180614_214734.jpg", "IMG_20181001_185137.jpg"};

    private WebClient webClient;

    @BeforeEach
    void setUp() {
        // https://www.baeldung.com/spring-5-webclient
        // https://www.baeldung.com/webflux-webclient-parameters
        this.webClient = WebClient
                .builder()
                .baseUrl("http://localhost:9100")
                .filter(logRequest())
                .build();
    }

    @ParameterizedTest
    @ArgumentsSource(TestImagesProvider.class)
    void scale(Resource image) throws IOException {

        ByteArrayInputStream bImg = webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("resize")
                        .queryParam("width", 150)
                        .build())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_JPEG_VALUE)
                .body(BodyInserters.fromResource(image))
                .accept(MediaType.IMAGE_JPEG)
                .retrieve()
                .bodyToMono(byte[].class)
                .map(ByteArrayInputStream::new)
                .block();

        BufferedImage img = ImageIO.read(bImg);

        assertThat(img.getWidth(), CoreMatchers.is(150));

    }

    private static class TestImagesProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) {
            return Stream.of(images)
                    .map(image -> new ClassPathResource("images/" + image))
                    .map(Arguments::of);
        }
    }

    private ExchangeFilterFunction logRequest() {
        return (clientRequest, next) -> {
            LOG.info("Request: {} {}", clientRequest.method(), clientRequest.url());
            clientRequest.headers()
                    .forEach((name, values) -> values.forEach(value -> LOG.info("{}={}", name, value)));
            return next.exchange(clientRequest);
        };
    }

}
