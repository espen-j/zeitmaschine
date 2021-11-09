package io.zeitmaschine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import io.zeitmaschine.s3.S3Config;
import io.zeitmaschine.s3.S3Repository;

@Component
@Profile("demo")
public class DemoInitializer {

    private final String bucket;
    private final S3Repository repository;

    @Value("${demo.dir}")
    Path demoDir;

    @Autowired
    public DemoInitializer(S3Repository repository, S3Config config) {
        this.repository = repository;
        this.bucket = config.getBucket();
    }

    public void bootstrapDemo() {
        try (Stream<Path> paths = Files.walk(demoDir)) {
            paths
                    .filter(Files::isRegularFile)
                    .forEach(path -> repository.put(bucket, path.getFileName().toString(), new FileSystemResource(path), MediaType.IMAGE_JPEG_VALUE));
        } catch (IOException e) {
            throw new RuntimeException("Error initializing demo content.", e);
        }
    }

}
