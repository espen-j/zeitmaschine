package io.zeitmaschine.s3;

import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.DeleteBucketNotificationArgs;
import io.minio.Directive;
import io.minio.GetBucketNotificationArgs;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.Result;
import io.minio.SetBucketNotificationArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.EventType;
import io.minio.messages.Item;
import io.minio.messages.NotificationConfiguration;
import io.minio.messages.QueueConfiguration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class S3Repository {

    private final static Logger log = LoggerFactory.getLogger(S3Repository.class.getName());
    public static final String UNKNOWN_CONTENT_TYPE = "unknown";

    private final String host;
    private final String bucket;
    private final String cacheBucket;
    private final boolean webhook;

    private MinioClient minioClient;

    @Autowired
    public S3Repository(S3Config config) {
        this.host = config.getHost();
        this.webhook = config.isWebhook();
        this.bucket = config.getBucket();
        this.cacheBucket = config.getCacheBucket();

        String key = config.getAccess().getKey();
        String secret = config.getAccess().getSecret();

        log.info("s3 host: {}", host);
        log.info("s3 bucket: {}", bucket);
        log.info("s3 cache-bucket: {}", cacheBucket);
        log.info("s3 access key: {}", key);
        log.info("s3 webhook: {}", webhook);

        this.minioClient = MinioClient.builder()
                .endpoint(host)
                .credentials(key, secret).build();
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
        StatObjectArgs stat = StatObjectArgs.builder()
                .bucket(bucket)
                .object(key)
                .build();
        try {
            StatObjectResponse response = minioClient.statObject(stat);
            // mark the difference to io.zeitmaschine.s3.S3Repository.get(java.lang.String)
            // userMetaData and response are different in these two cases.
            String contentType = response.contentType();
            S3Entry entry = S3Entry.builder()
                    .key(key) // or response.object() ?
                    .size(response.size())
                    .contentType(contentType)
                    .resourceSupplier(getResourceSupplier(bucket, key))
                    .build();
            entry = Processor.from(entry)
                    .process(response.userMetadata());

            return Mono.just(entry);

        } catch (ErrorResponseException e) {
            switch (e.errorResponse().code()) {
            case "NoSuchKey":
            case "ResourceNotFound":
                log.debug("No object found for '{}'.", key);
                return Mono.empty();
            default:
                // Mono.error ?
                throw new RuntimeException(String.format("Failed to fetch object '%s' from S3.", key), e);
            }
        } catch (Exception e) {
            // Mono.error ?
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

    public void metaData(String bucket, String key, Map<String, String> metaData) {
        try {
            CopyObjectArgs build = CopyObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .source(CopySource.builder()
                            .bucket(bucket)
                            .object(key)
                            .build())
                    .metadataDirective(Directive.COPY)
                    .userMetadata(metaData)
                    .build();
            minioClient.copyObject(build);
        } catch (Exception e) {
            throw new RuntimeException("Error while writing object '%s' to s3.".formatted(key), e);
        }
    }

    /**
     * Fetches all objects from the bucket with the according prefix. Empty "" prefix will fetch all objects.
     * This will not retrieve the binary itself, only the object's metadata.
     *
     * @param prefix E.g. the folder name.
     */
    public Flux<S3Entry> get(String prefix) {
        try {
            ListObjectsArgs listArgs = ListObjectsArgs.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .recursive(true)
                    .includeUserMetadata(true)
                    .build();
            return Flux.fromIterable(minioClient.listObjects(listArgs))
                    .flatMap(itemResult -> toS3Entry(itemResult)
                            .doOnError(ex -> log.error("Failed to process image.", ex))
                            .onErrorResume(ex -> Mono.empty()));
        } catch (Exception e) {
            log.error("Error fetching objects with prefix '{}' from s3: ", prefix, e);
            return Flux.error(e);
        }
    }

    // TODO: Function field?
    private Mono<S3Entry> toS3Entry(Result<Item> itemResult) {
        try {
            Item item = itemResult.get();
            String contentType = item.userMetadata().getOrDefault("content-type", UNKNOWN_CONTENT_TYPE);
            String objectKey = item.objectName();
            Map<String, String> metaData = item.userMetadata();

            S3Entry entry = S3Entry.builder()
                    .key(objectKey)
                    .contentType(contentType)
                    .size(item.size())
                    .resourceSupplier(getResourceSupplier(bucket, objectKey))
                    .build();
            entry = Processor.from(entry).process(metaData);
            return Mono.just(entry);
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    /*
    Supplier to fetch the remote S3 object on demand.
    TODO: Supplier field?
     */
    private Supplier<Resource> getResourceSupplier(String bucket, String key) {
        return () -> {
            try {
                InputStream i = minioClient.getObject(GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(key)
                        .build());
                return new InputStreamResource(i);
                // Alternatively: new org.springframework.core.io.ByteArrayResource(i.readAllBytes());
                // Not sure what's smarter: InputStream is one use and throw away, afterwards it has to go over the wire.
                // OTOH InputStream should be able to start processing further while it's being downloaded. Plus lower
                // memory usage?
                // As long as it's used only one time, this is better I guess. Don't forget to close the stream!
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }
}
