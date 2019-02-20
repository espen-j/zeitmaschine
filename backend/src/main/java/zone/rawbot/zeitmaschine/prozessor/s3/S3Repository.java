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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import zone.rawbot.zeitmaschine.prozessor.image.Dimension;
import zone.rawbot.zeitmaschine.prozessor.image.Scaler;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.Consumer;

@Service
public class S3Repository {

    private final static Logger log = LoggerFactory.getLogger(S3Repository.class.getName());

    public static final String BUCKET_NAME = "media";
    public static final String BUCKET_CACHE_NAME = "media-cache";

    private final String host;
    private final String key;
    private final String secret;

    private MinioClient minioClient;

    @Autowired
    public S3Repository(S3Config config) {
        this.host = config.getHost();
        this.key = config.getAccess().getKey();
        this.secret = config.getAccess().getSecret();
    }

    @PostConstruct
    private void init() throws InvalidPortException, InvalidEndpointException {
        log.info("s3 host: {} key: {}", host, key);
        this.minioClient = new MinioClient(host, key, secret);
        try {
            if (!minioClient.bucketExists(BUCKET_CACHE_NAME)) {
                minioClient.makeBucket(BUCKET_CACHE_NAME);
            }
        } catch (Exception e) {
            log.error("Failed to query or create cache bucket..", e);
        }
    }

    public byte[] getImageAsData(String key, Dimension dimension) {

        Optional<BufferedImage> cached = loadCached(key, dimension);

        if (cached.isPresent()) {
            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                ImageIO.write(cached.get(), "jpg", os);
                return os.toByteArray();
            } catch (IOException e) {
                log.error("Error writing cached thumb to bytes object.", e);
            }
        } else {
            try (InputStream object = minioClient.getObject(BUCKET_NAME, key)) {
                BufferedImage image = Scaler.scale(ImageIO.read(object), dimension);

                ByteArrayOutputStream os = new ByteArrayOutputStream();
                ImageIO.write(image, "jpg", os);

                byte[] bytes = os.toByteArray();
                cache(key, bytes, dimension);
                return bytes;
            } catch (Exception e) {
                // FIXME close streams
                log.error("Error getting object.", e);
            }
        }

        return null;
    }

    private void cache(String key, byte[] thumbnail, Dimension dimension) {
        try (InputStream stream = new ByteArrayInputStream(thumbnail)) {
            minioClient.putObject(BUCKET_CACHE_NAME, getThumbName(key, dimension), stream, MediaType.IMAGE_JPEG_VALUE);
        } catch (Exception e) {
            log.error("Failed to cache scaled image.", e);
        }
    }

    private static String getThumbName(String key, Dimension dimension) {
        return Paths.get(dimension.name(), key).toString();
    }

    private Optional<BufferedImage> loadCached(String key, Dimension dimension) {
        try (InputStream object = minioClient.getObject(BUCKET_CACHE_NAME, getThumbName(key, dimension))) {
            return Optional.of(ImageIO.read(object));
        } catch (Exception e) {
            log.info("Failed to fetch cached thumbnail.");
        }
        return Optional.empty();
    }

    public void getImages(Consumer<Image> indexer) {

        try {
            minioClient.listObjects(BUCKET_NAME).forEach(itemResult -> {
                try {
                    Item item = itemResult.get();
                    String name = item.objectName();
                    Image image = getImage(name);
                    indexer.accept(image);
                } catch (Exception e) {
                    log.error("Failed to get item with name.", e);
                }
            });
        } catch (Exception e) {
            log.error("Error fetching all objects from s3: " + e);
        }
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
}
