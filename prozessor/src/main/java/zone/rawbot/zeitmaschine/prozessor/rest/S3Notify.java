package zone.rawbot.zeitmaschine.prozessor.rest;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.jayway.jsonpath.JsonPath;
import io.minio.MinioClient;
import io.minio.ObjectStat;
import io.minio.Result;
import io.minio.errors.InvalidEndpointException;
import io.minio.errors.InvalidPortException;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * S3 notify endpoint.
 */
@RestController
public class S3Notify {

    private final static Logger log = LoggerFactory.getLogger(S3Notify.class.getName());

    public static final String ENDPOINT = "http://localhost:9000";
    public static final String MINIO_ACCESS_KEY = "test";
    public static final String MINIO_SECRET_KEY = "testtest";
    public static final String BUCKET_NAME = "media";
    private MinioClient minioClient;

    @PostConstruct
    private void init() throws InvalidPortException, InvalidEndpointException {
        this.minioClient = new MinioClient(ENDPOINT, MINIO_ACCESS_KEY, MINIO_SECRET_KEY);
    }

    @PutMapping("api/webhook")
    public ResponseEntity create(@RequestBody String json) {
        return ResponseEntity.ok().build();
    }

    @PostMapping("api/webhook")
    public ResponseEntity update(@RequestBody String json) {

        List<String> keys = JsonPath.read(json, "$.Records[*].s3.object.key");

        keys.stream()
                .map(key -> getImage(key))
                .filter(optional -> optional.isPresent())
                .peek(url -> log.info(url.get()))
                .findAny();
        return ResponseEntity.ok().build();
    }

    private  Optional<String> getImage(String key) {
        try {
            ObjectStat stat = minioClient.statObject(BUCKET_NAME, key);
            String contentType = stat.contentType();
            if (contentType.equals(MediaType.IMAGE_JPEG_VALUE)) {
                InputStream inputStream = minioClient.getObject(BUCKET_NAME, key);
                Metadata metadata = ImageMetadataReader.readMetadata(inputStream);
                metadata.getDirectories().forEach(directory -> log.info(directory.getName()));
            }
            return Optional.of(minioClient.getObjectUrl(BUCKET_NAME, key));

        } catch (Exception e) {
            log.error("Could not get image url for '{}'.", key, e);
        }
        return Optional.empty();
    }

    private void getImages() {
        try {
            boolean found = minioClient.bucketExists(BUCKET_NAME);
            if (found) {
                Iterable<Result<Item>> myObjects = minioClient.listObjects(BUCKET_NAME);
                for (Result<Item> result : myObjects) {
                    Item item = result.get();
                    System.out.println(item.lastModified() + ", " + item.size() + ", " + item.objectName());
                }
            } else {
                System.out.println("bucket name does not exist");
            }
        } catch (Exception e) {
            System.out.println("Error occurred: " + e);
        }
    }

}
