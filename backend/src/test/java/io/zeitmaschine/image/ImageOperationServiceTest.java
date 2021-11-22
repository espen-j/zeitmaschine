package io.zeitmaschine.image;

import static org.hamcrest.MatcherAssert.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import io.zeitmaschine.TestImagesProvider;

@Testcontainers
public class ImageOperationServiceTest {

    private static final Logger LOG = LoggerFactory.getLogger(ImageOperationServiceTest.class.getName());

    private static final int EXPOSED_PORT = 8088;

    // will be shared between test methods
    @Container
    private static GenericContainer container = new GenericContainer(DockerImageName.parse("h2non/imaginary:1.2.4"))
                .withEnv("PORT", String.valueOf(EXPOSED_PORT))
                .withExposedPorts(EXPOSED_PORT);

    private ImageOperationService operationService;
    private ImageOperationConfig config;

    @BeforeEach
    void setUp() {
        String host = "http://" + container.getHost() + ":" + container.getFirstMappedPort();
        this.config = new ImageOperationConfig();
        config.setHost(host);
        this.operationService = new ImageOperationService(config);
    }

    @ParameterizedTest
    @ArgumentsSource(TestImagesProvider.class)
    public void resizeOperation(Resource image) throws IOException {
        Resource thumbResource = operationService.resize(image, Dimension.SMALL).block();

        BufferedImage thumbnail = ImageIO.read(thumbResource.getInputStream());

        assertThat(thumbnail.getWidth(), CoreMatchers.is(Dimension.SMALL.getSize()));
    }

    // InputStream made some trouble.. Keep in mind that the InputStreamResource is only usable once.
    // Update: this does indeed not work, using ByteArrayResource in productive code:
    // see: ImageService#getImageByDimension and S3Repository#get
    @ParameterizedTest
    @ArgumentsSource(TestImagesProvider.class)
    public void inputStreamResource(Resource image) throws IOException {

        Resource thumbResource = operationService.resize(image, Dimension.SMALL).block();

        BufferedImage thumbnail = ImageIO.read(thumbResource.getInputStream());

        assertThat(thumbnail.getWidth(), CoreMatchers.is(Dimension.SMALL.getSize()));
    }


    /*
       Naked Imaginary API Call.
     */
    @ParameterizedTest
    @ArgumentsSource(TestImagesProvider.class)
    void imaginary(Resource image) throws IOException {
        // https://www.baeldung.com/spring-5-webclient
        // https://www.baeldung.com/webflux-webclient-parameters
        WebClient webClient = WebClient
                .builder()
                .baseUrl(config.getHost())
                .filter(logRequest())
                .build();

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

    private ExchangeFilterFunction logRequest() {
        return (clientRequest, next) -> {
            LOG.info("Request: {} {}", clientRequest.method(), clientRequest.url());
            clientRequest.headers()
                    .forEach((name, values) -> values.forEach(value -> LOG.info("{}={}", name, value)));
            return next.exchange(clientRequest);
        };
    }
}
