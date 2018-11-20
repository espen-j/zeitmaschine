package zone.rawbot.zeitmaschine.prozessor.rest;

import io.minio.MinioClient;
import io.minio.Result;
import io.minio.errors.MinioException;
import io.minio.messages.Item;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * S3 notify endpoint.
 */
@RestController
public class S3Notify {

    @PutMapping("api/webhook")
    public ResponseEntity create(@RequestBody String body) {
        return ResponseEntity.ok().build();
    }

    @PostMapping("api/webhook")
    public ResponseEntity update(@RequestBody String body) {
        return ResponseEntity.ok().build();
    }

    private void getImages() {
        try {
            /* play.minio.io for test and development. */
            MinioClient minioClient = new MinioClient("http://localhost:9000", "test",
                    "testtest");

            // Check whether 'my-bucketname' exist or not.
            boolean found = minioClient.bucketExists("pics");
            if (found) {
                // List objects from 'my-bucketname'
                Iterable<Result<Item>> myObjects = minioClient.listObjects("pics");
                for (Result<Item> result : myObjects) {
                    Item item = result.get();
                    System.out.println(item.lastModified() + ", " + item.size() + ", " + item.objectName());
                }
            } else {
                System.out.println("bucket name does not exist");
            }
        } catch (MinioException | NoSuchAlgorithmException | IOException | InvalidKeyException | XmlPullParserException e) {
            System.out.println("Error occurred: " + e);
        }
    }

}
