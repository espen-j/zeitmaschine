package io.zeitmaschine.image;

import static org.hamcrest.MatcherAssert.assertThat;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public class ImageOperationServiceIT {
    private static final String[] images = {"IMG_20161208_024708.jpg", "IMG_20180614_214734.jpg", "IMG_20181001_185137.jpg"};

    private static final int EXPOSED_PORT = 8088;

    // will be shared between test methods
    @Container
    private static GenericContainer container = new GenericContainer(DockerImageName.parse("h2non/imaginary:1.2.4"))
                .withEnv("PORT", String.valueOf(EXPOSED_PORT))
                .withExposedPorts(EXPOSED_PORT);

    private ImageOperationService operationService;

    @BeforeEach
    void setUp() {
        ImageOperationConfig config = new ImageOperationConfig();
        String host = "http://" + container.getHost() + ":" + container.getFirstMappedPort();
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
    @Test
    public void inputStreamResource() throws IOException {
        InputStream stream = ClassLoader.getSystemResourceAsStream("images/" + images[0]);

        Resource resource = new InputStreamResource(stream);
        Resource thumbResource = operationService.resize(resource, Dimension.SMALL).block();

        BufferedImage thumbnail = ImageIO.read(thumbResource.getInputStream());

        assertThat(thumbnail.getWidth(), CoreMatchers.is(Dimension.SMALL.getSize()));
    }


    private static class TestImagesProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) {
            return Stream.of(images)
                    .map(image -> new ClassPathResource("images/" + image))
                    .map(Arguments::of);
        }
    }
}
