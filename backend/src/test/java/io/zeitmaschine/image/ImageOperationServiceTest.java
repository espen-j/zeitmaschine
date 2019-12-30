package io.zeitmaschine.image;

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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;

public class ImageOperationServiceTest {
    private static final String[] images = {"IMG_20161208_024708.jpg", "IMG_20180614_214734.jpg", "IMG_20181001_185137.jpg"};

    private ImageOperationService operationService;

    @BeforeEach
    void setUp() {
        ImageOperationConfig config = new ImageOperationConfig();
        config.setHost("http://localhost:9100");
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
