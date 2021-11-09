package io.zeitmaschine;

import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import io.zeitmaschine.s3.S3Config;
import io.zeitmaschine.s3.S3Repository;

@ExtendWith(MockitoExtension.class)
class DemoInitializerTest {

    @Mock
    private S3Repository s3Repository;

    @Test
    void bootstrap(@TempDir Path tempDir) throws Exception {
        // GIVEN
        Resource image = new ClassPathResource("images/IMG_20181001_185137.jpg");
        Path file = tempDir.resolve(image.getFilename());
        Files.copy(image.getInputStream(), file);

        S3Config config = new S3Config();
        config.setBucket("test");

        DemoInitializer initializer = new DemoInitializer(s3Repository, config);
        initializer.demoDir = tempDir;

        // WHEN
        initializer.bootstrapDemo();

        // THEN
        verify(s3Repository, times(1)).put(eq("test"), eq(file.getFileName().toString()), any(Resource.class), any());
    }
}