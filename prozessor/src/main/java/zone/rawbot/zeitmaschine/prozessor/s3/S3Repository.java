package zone.rawbot.zeitmaschine.prozessor.s3;

import io.minio.MinioClient;
import io.minio.ObjectStat;
import io.minio.errors.InvalidEndpointException;
import io.minio.errors.InvalidPortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
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

    public byte[] getImageAsData(String key) {
        try (InputStream object = minioClient.getObject(BUCKET_NAME, key)) {
            BufferedImage image = scaleImage(ImageIO.read(object), 250, 250, Color.BLACK);

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", os);

            return os.toByteArray();
        } catch (Exception e) {
            log.error("Error getting object.", e);
        }
        return null;
    }

    public Optional<Image> getImage(String key) {
        try {
            ObjectStat stat = minioClient.statObject(BUCKET_NAME, key);
            String contentType = stat.contentType();
            if (contentType.equals(MediaType.IMAGE_JPEG_VALUE)) {
                Image image = Image.from(key, key);
                return Optional.of(image);
            }

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

    public BufferedImage scaleImage(BufferedImage source, int width, int height,
                                    Color background) {
        int imgWidth = source.getWidth();
        int imgHeight = source.getHeight();
        if (imgWidth*height < imgHeight*width) {
            width = imgWidth*height/imgHeight;
        } else {
            height = imgHeight*width/imgWidth;
        }
        BufferedImage scaled = new BufferedImage(width, height,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setBackground(background);
            g.clearRect(0, 0, width, height);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return scaled;
    }

}
