package io.zeitmaschine.s3;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InvalidEndpointException;
import io.minio.errors.InvalidPortException;
import io.minio.messages.EventType;
import io.minio.messages.Filter;
import io.minio.messages.NotificationConfiguration;
import io.minio.messages.QueueConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

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
     * <p>
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

    public Flux<Image> getImages() {

        try {
            return Flux.fromIterable(minioClient.listObjects(BUCKET_NAME))
                    .map(itemResult -> {
                        try {
                            return itemResult.get();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .flatMap(item -> {
                        // this is broken due to the key not being part of the object (or is it?)
                        String key = item.objectName();
                        return get(BUCKET_NAME, key)
                                .map(resource -> toImage(key, resource));
                    });
        } catch (Exception e) {
            log.error("Error fetching all objects from s3: " + e);
            return Flux.error(e);
        }
    }

    public Image toImage(String key, Resource resource) {

        // FIXME contenttype not checked!
        try {
            Image.Builder builder = Image.from(key);

            Metadata metadata = ImageMetadataReader.readMetadata(resource.getInputStream());
            Optional<ExifSubIFDDirectory> subIFDDirectory = Optional.ofNullable(metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class));
            subIFDDirectory.ifPresent(subIFD -> builder.createDate(subIFD.getDateOriginal()));

            Optional<GpsDirectory> gpsDirectory = Optional.ofNullable(metadata.getFirstDirectoryOfType(GpsDirectory.class));
            gpsDirectory.ifPresent(gps -> {
                GeoLocation geoLocation = gps.getGeoLocation();
                if (geoLocation != null)
                    builder.location(geoLocation.getLatitude(), geoLocation.getLongitude());
            });
            return builder.build();
        } catch (IOException | ImageProcessingException e) {
            throw new RuntimeException("Error reading metadata from image.", e);
        }
    }

    public Mono<Resource> get(String bucket, String key) {
        try (InputStream object = minioClient.getObject(bucket, key)) {
            return Mono.just(new ByteArrayResource(object.readAllBytes()));
        } catch (ErrorResponseException e) {
            switch (e.errorResponse().errorCode()) {
                case NO_SUCH_KEY:
                case RESOURCE_NOT_FOUND:
                    log.debug("No object found for '{}'.", key);
                    return Mono.empty();
                default:
                    throw new RuntimeException(String.format("Failed to fetch object '%s' from S3.", key), e);
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format("Failed to fetch object '%s' from S3.", key), e);
        }

    }

    public void put(String bucket, String key, Resource resource, String contentType) throws Exception {
        minioClient.putObject(bucket, key, resource.getInputStream(), contentType);
    }
}
