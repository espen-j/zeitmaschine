package zone.rawbot.zeitmaschine.prozessor.s3;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import io.minio.MinioClient;
import io.minio.errors.InvalidEndpointException;
import io.minio.errors.InvalidPortException;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class S3Repository {

    private final static Logger log = LoggerFactory.getLogger(S3Repository.class.getName());

    public static final String ENDPOINT = "http://localhost:9000";
    public static final String MINIO_ACCESS_KEY = "test";
    public static final String MINIO_SECRET_KEY = "testtest";
    public static final String BUCKET_NAME = "media";
    public static final String BUCKET_CACHE_NAME = "media-cache";

    private MinioClient minioClient;

    @PostConstruct
    private void init() throws InvalidPortException, InvalidEndpointException {
        this.minioClient = new MinioClient(ENDPOINT, MINIO_ACCESS_KEY, MINIO_SECRET_KEY);
        try {
            if (!minioClient.bucketExists(BUCKET_CACHE_NAME)) {
                minioClient.makeBucket(BUCKET_CACHE_NAME);
            }
        } catch (Exception e) {
            log.error("Failed to query or create cache bucket..", e);
        }
    }

    public byte[] getImageAsData(String key) {

        Optional<BufferedImage> thumb = getThumb(key);

        if (thumb.isPresent()) {
            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                ImageIO.write(thumb.get(), "jpg", os);
                return os.toByteArray();
            } catch (IOException e) {
                log.error("Error writing cached thumb to bytes object.", e);
            }
        } else {
            try (InputStream object = minioClient.getObject(BUCKET_NAME, key)) {
                BufferedImage image = scaleImage(ImageIO.read(object), 250, 250);

                ByteArrayOutputStream os = new ByteArrayOutputStream();
                ImageIO.write(image, "jpg", os);

                byte[] bytes = os.toByteArray();
                store(key, bytes);
                return bytes;
            } catch (Exception e) {
                // FIXME close streams
                log.error("Error getting object.", e);
            }
        }

        return null;
    }

    private void store(String key, byte[] thumbnail) {
        try (InputStream stream = new ByteArrayInputStream(thumbnail)) {
            minioClient.putObject(BUCKET_CACHE_NAME, getThumbName(key), stream, MediaType.IMAGE_JPEG_VALUE);
        } catch (Exception e) {
            log.error("Failed to store thumbnail.", e);
        }
    }

    private static String getThumbName(String key) {
        return ".scaled/small/" + key;
    }

    private Optional<BufferedImage> getThumb(String key) {
        try (InputStream object = minioClient.getObject(BUCKET_CACHE_NAME, getThumbName(key))) {
            return Optional.of(ImageIO.read(object));
        } catch (Exception e) {
            log.info("Failed to fetch cached thumbnail.");
        }
        return Optional.empty();
    }

    public List<Image> getImages() {

        // FIXME memory?
        List<Image> images = new ArrayList<>();

        try {
            minioClient.listObjects(BUCKET_NAME).forEach(itemResult -> {
                try {
                    Item item = itemResult.get();
                    String name = item.objectName();
                    Image image = getImage(name);
                    images.add(image);
                } catch (Exception e) {
                    log.error("Failed to get item with name.", e);
                }
            });
        } catch (Exception e) {
            log.error("Error fetching all objects from s3: " + e);
        }
        return images;
    }

    public Image getImage(String name) {
        Image.Builder builder = Image.from(name);

        // FIXME contenttype not checked!
        try (InputStream stream = minioClient.getObject(BUCKET_NAME, name)) {
            Metadata metadata = ImageMetadataReader.readMetadata(stream);

            Optional<ExifSubIFDDirectory> subIFDDirectory = Optional.ofNullable(metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class));
            subIFDDirectory.ifPresent(subIFD -> builder.createDate(subIFD.getDateOriginal()));

            Optional<GpsDirectory> gpsDirectory = Optional.ofNullable(metadata.getFirstDirectoryOfType(GpsDirectory.class));
            gpsDirectory.ifPresent(gps -> {
                GeoLocation geoLocation = gps.getGeoLocation();
                if (geoLocation != null)
                builder.location(geoLocation.getLatitude(), geoLocation.getLongitude());
            });

        } catch (Exception e) {
            log.error("Error fetching image.", e);
        }
        return builder.build();
    }

    public BufferedImage scaleImage(BufferedImage source, int width, int height) {
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
            g.setBackground(Color.BLACK);
            g.clearRect(0, 0, width, height);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return scaled;
    }

}
