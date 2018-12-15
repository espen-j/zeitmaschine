package zone.rawbot.zeitmaschine.prozessor.s3;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import io.minio.MinioClient;
import io.minio.ObjectStat;
import io.minio.errors.InvalidEndpointException;
import io.minio.errors.InvalidPortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Service
public class S3Repository {

    private final static Logger log = LoggerFactory.getLogger(S3Repository.class.getName());

    public static final String ENDPOINT = "http://localhost:9000";
    public static final String MINIO_ACCESS_KEY = "test";
    public static final String MINIO_SECRET_KEY = "testtest";
    public static final String BUCKET_NAME = "media";

    private MinioClient minioClient;

    @PostConstruct
    private void init() throws InvalidPortException, InvalidEndpointException {
        this.minioClient = new MinioClient(ENDPOINT, MINIO_ACCESS_KEY, MINIO_SECRET_KEY);
    }

    public Resource getImageAsData(String key) {
        try {
            InputStream object = minioClient.getObject(BUCKET_NAME, key);
            BufferedImage image = scaleImage(ImageIO.read(object), 250, 250, Color.BLACK);

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ImageIO.write(image, "gif", os);
            InputStream is = new ByteArrayInputStream(os.toByteArray());

            InputStreamResource resource = new InputStreamResource(is);
            return resource;
        } catch (Exception e) {
            log.error("Error getting object.", e);
        }
        return null;
    }

    public Optional<String> getImage(String key) {
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

    public Flux<Image> getImages() {
        try {
            Stream<Image> items = StreamSupport.stream(minioClient.listObjects(BUCKET_NAME).spliterator(), false)
                    .map(itemResult -> {
                        try {
                            return itemResult.get();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .map(item -> Image.from(item.objectName(), item.objectName()));

            return Flux.fromStream(items);
        } catch (Exception e) {
            log.error("Error occurred: " + e);
        }
        return Flux.empty();
    }

    public BufferedImage scaleImage(BufferedImage img, int width, int height,
                                    Color background) {
        int imgWidth = img.getWidth();
        int imgHeight = img.getHeight();
        if (imgWidth*height < imgHeight*width) {
            width = imgWidth*height/imgHeight;
        } else {
            height = imgHeight*width/imgWidth;
        }
        BufferedImage newImage = new BufferedImage(width, height,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = newImage.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setBackground(background);
            g.clearRect(0, 0, width, height);
            g.drawImage(img, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return newImage;
    }

}
