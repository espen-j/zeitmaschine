package zone.rawbot.zeitmaschine.prozessor.s3;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import io.minio.MinioClient;
import io.minio.errors.InvalidEndpointException;
import io.minio.errors.InvalidPortException;
import io.minio.messages.EventType;
import io.minio.messages.Filter;
import io.minio.messages.Item;
import io.minio.messages.NotificationConfiguration;
import io.minio.messages.QueueConfiguration;
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
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
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
    private final boolean webhook;

    private MinioClient minioClient;

    @Autowired
    public S3Repository(S3Config config) {
        this.host = config.getHost();
        this.key = config.getAccess().getKey();
        this.secret = config.getAccess().getSecret();
        this.webhook = config.isWebhook();
    }

    @PostConstruct
    private void init() throws InvalidPortException, InvalidEndpointException {
        log.info("s3 host: {}", host);
        log.info("s3 access key: {}", key);
        log.info("s3 webhook: {}", webhook);
        this.minioClient = new MinioClient(host, key, secret);

        try {
            if (!minioClient.bucketExists(BUCKET_NAME)) {
                minioClient.makeBucket(BUCKET_NAME);
            }
        } catch (Exception e) {
            log.error("Failed to query or create bucket '{}'.", BUCKET_NAME, e);
        }

        try {
            if (!minioClient.bucketExists(BUCKET_CACHE_NAME)) {
                minioClient.makeBucket(BUCKET_CACHE_NAME);
            }
        } catch (Exception e) {
            log.error("Failed to query or create bucket '{}'", BUCKET_CACHE_NAME, e);
        }

        try {
            if (webhook) {
                enableWebHookNotification();
            } else {
                minioClient.removeAllBucketNotification(BUCKET_NAME);
            }
        } catch (Exception e) {
            log.error("Failed to update notifications for bucket '{}'", BUCKET_NAME, e);
        }
    }

    /**
     * Enables notifications over webhook for the bucket. The endpoint to be notified still needs to be set in
     * configuration.
     *
     * see https://github.com/minio/minio-java/blob/master/examples/SetBucketNotification.java
     */
    private void enableWebHookNotification() throws Exception {

        NotificationConfiguration notificationConfiguration = minioClient.getBucketNotification(BUCKET_NAME);

        List<QueueConfiguration> queueConfigurationList = notificationConfiguration.queueConfigurationList();
        if (queueConfigurationList.size() > 0) {
            // skip if already created, otherwise error concerning overlapping suffixes
            return;
        }

        // Add a new SQS configuration.
        QueueConfiguration queueConfiguration = new QueueConfiguration();
        queueConfiguration.setQueue("arn:minio:sqs::1:webhook");

        List<EventType> eventList = new LinkedList<>();
        eventList.add(EventType.OBJECT_CREATED_PUT);
        eventList.add(EventType.OBJECT_REMOVED_DELETE);
        queueConfiguration.setEvents(eventList);

        Filter filter = new Filter();
        filter.setSuffixRule(".jpg");
        queueConfiguration.setFilter(filter);

        queueConfigurationList.add(queueConfiguration);
        notificationConfiguration.setQueueConfigurationList(queueConfigurationList);

        // Set updated notification configuration.
        minioClient.setBucketNotification(BUCKET_NAME, notificationConfiguration);
        log.info("Bucket notification set successfully");

    }

    public byte[] getImageAsData(String key, Dimension dimension) throws Exception {

        Optional<BufferedImage> cached = loadCached(key, dimension);

        if (cached.isPresent()) {
            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                ImageIO.write(cached.get(), "jpg", os);
                return os.toByteArray();
            }
        } else {
            try (InputStream object = minioClient.getObject(BUCKET_NAME, key);
                 ByteArrayOutputStream os = new ByteArrayOutputStream()) {

                BufferedImage image = Scaler.scale(ImageIO.read(object), dimension);

                ImageIO.write(image, "jpg", os);

                byte[] bytes = os.toByteArray();
                cache(key, bytes, dimension);
                return bytes;
            }
        }
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

        // FIXME contenttype not checked!
        try (InputStream stream = minioClient.getObject(BUCKET_NAME, name)) {
            Image.Builder builder = Image.from(name);

            Metadata metadata = ImageMetadataReader.readMetadata(stream);

            Optional<ExifSubIFDDirectory> subIFDDirectory = Optional.ofNullable(metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class));
            subIFDDirectory.ifPresent(subIFD -> builder.createDate(subIFD.getDateOriginal()));

            Optional<GpsDirectory> gpsDirectory = Optional.ofNullable(metadata.getFirstDirectoryOfType(GpsDirectory.class));
            gpsDirectory.ifPresent(gps -> {
                GeoLocation geoLocation = gps.getGeoLocation();
                if (geoLocation != null)
                    builder.location(geoLocation.getLatitude(), geoLocation.getLongitude());
            });
            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException("Error fetching image.", e);
        }
    }
}
