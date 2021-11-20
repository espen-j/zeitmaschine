package io.zeitmaschine.s3;

import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import io.minio.BucketExistsArgs;
import io.minio.DeleteBucketNotificationArgs;
import io.minio.GetBucketNotificationArgs;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketNotificationArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.EventType;
import io.minio.messages.NotificationConfiguration;
import io.minio.messages.QueueConfiguration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class S3Repository {

    private final static Logger log = LoggerFactory.getLogger(S3Repository.class.getName());

    private final String host;
    private final String bucket;
    private final String cacheBucket;
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
        this.bucket = config.getBucket();
        this.cacheBucket = config.getCacheBucket();

        log.info("s3 host: {}", host);
        log.info("s3 bucket: {}", bucket);
        log.info("s3 cache-bucket: {}", cacheBucket);
        log.info("s3 access key: {}", key);
        log.info("s3 webhook: {}", webhook);
    }

    public boolean health() {
        try {
            boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            boolean cacheExists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(cacheBucket).build());
            return bucketExists && cacheExists;
        } catch (Exception e) {
            return false;
        }
    }

    public void initBucket() {
        this.minioClient = MinioClient.builder().endpoint(host).credentials(key, secret).build();
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            log.error("Failed to query or create bucket '{}'.", bucket, e);
        }

        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(cacheBucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(cacheBucket).build());
            }
        } catch (Exception e) {
            log.error("Failed to query or create bucket '{}'", cacheBucket, e);
        }

        try {
            if (webhook) {
                enableWebHookNotification();
            } else {
                minioClient.deleteBucketNotification(DeleteBucketNotificationArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            log.error("Failed to update notifications for bucket '{}'", bucket, e);
        }
    }

    /**
     * Enables notifications over webhook for the bucket. The endpoint to be notified still needs to be set in
     * configuration.
     * <p>
     * see https://github.com/minio/minio-java/blob/master/examples/SetBucketNotification.java
     */
    private void enableWebHookNotification() throws Exception {

        NotificationConfiguration notificationConfiguration = minioClient.getBucketNotification(GetBucketNotificationArgs.builder().bucket(bucket).build());

        List<QueueConfiguration> queueConfigurationList = notificationConfiguration.queueConfigurationList();
        if (queueConfigurationList.size() > 0) {
            // skip if already created, otherwise error concerning overlapping suffixes
            return;
        }
        queueConfigurationList = new LinkedList<>(); // create new list

        // Add a new SQS configuration.
        QueueConfiguration queueConfiguration = new QueueConfiguration();
        // printed on successful startup: chicken egg problem.
        queueConfiguration.setQueue("arn:minio:sqs::zm:webhook");

        List<EventType> eventList = new LinkedList<>();
        eventList.add(EventType.OBJECT_CREATED_PUT);
        eventList.add(EventType.OBJECT_REMOVED_DELETE);
        queueConfiguration.setEvents(eventList);

        queueConfiguration.setSuffixRule(".jpg");

        queueConfigurationList.add(queueConfiguration);
        notificationConfiguration.setQueueConfigurationList(queueConfigurationList);

        // Set updated notification configuration.
        minioClient.setBucketNotification(SetBucketNotificationArgs.builder()
                .bucket(bucket)
                .config(notificationConfiguration)
                .build());
        log.info("Bucket notification set successfully");

    }

    public Mono<S3Entry> get(String bucket, String key) {
        try (InputStream object = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(key)
                .build())) {
            return Mono.just(S3Entry.of(key, new ByteArrayResource(object.readAllBytes())));
        } catch (ErrorResponseException e) {
            switch (e.errorResponse().code()) {
            case "NoSuchKey":
            case "ResourceNotFound":
                log.debug("No object found for '{}'.", key);
                return Mono.empty();
            default:
                throw new RuntimeException(String.format("Failed to fetch object '%s' from S3.", key), e);
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format("Failed to fetch object '%s' from S3.", key), e);
        }

    }

    public void put(String bucket, String key, Resource resource, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(resource.getInputStream(), resource.contentLength(), -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Error while writing object '%s' to s3.".formatted(key), e);
        }
    }

    /* This is a nasty function only used for indexing atm. Besides not scaling it needs
     * a hackish Tuple object to transport the key and resource.
     * The problem is that this application does not serve the JSON object to the client, elastic
     * does. So most methods related to images only need the raw image blob. This method serves the only
     * exception: The indexing to elastic.
     *
     */
    public Flux<S3Entry> get(String prefix) {
        try {
            return Flux.fromIterable(minioClient.listObjects(ListObjectsArgs.builder().bucket(bucket).prefix(prefix).recursive(true).build()))
                    .map(itemResult -> {
                        try {
                            return itemResult.get();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .flatMap(item -> get(bucket, item.objectName()));
        } catch (Exception e) {
            log.error("Error fetching objects with prefix '{}' from s3: ", prefix, e);
            return Flux.error(e);
        }
    }

}
