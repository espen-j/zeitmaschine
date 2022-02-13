package io.zeitmaschine.s3;

import java.util.Map;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

import io.zeitmaschine.index.IndexEndpoint;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MetaDataProcessingRepository implements S3Repository {

    private final static Logger LOG = LoggerFactory.getLogger(IndexEndpoint.class.getName());

    private final static Predicate<S3Entry> contentTypeFilter = s3Entry -> {
        String contentType = s3Entry.contentType();
        boolean contentTypeMatch = contentType.equals(MediaType.IMAGE_JPEG_VALUE);
        if (!contentTypeMatch) {
            LOG.info("Filtering '{}' with content-type '{}'.", s3Entry.key(), contentType);
        }
        return contentTypeMatch;
    };

    private S3Repository s3Repository;
    private Processor processor;


    MetaDataProcessingRepository(S3Repository s3Repository) {
        this.s3Repository = s3Repository;
        this.processor = new Processor(s3Entry -> metaData(s3Entry.key(), s3Entry.metaData(), s3Entry.contentType()));
    }

    @Override
    public boolean health() {
        return s3Repository.health();
    }

    @Override
    public void initBucket() {
        s3Repository.initBucket();
    }

    @Override
    public Mono<S3Entry> get(String bucket, String key) {
        return s3Repository.get(bucket, key)
                .filter(contentTypeFilter)
                .map(s3Entry -> processor.process(s3Entry));
    }

    @Override
    public void put(String bucket, String key, Resource resource, String contentType) {
        s3Repository.put(bucket, key, resource, contentType);
    }

    @Override
    public void metaData(String key, Map<String, String> metaData, String contentType) {
        s3Repository.metaData(key, metaData, contentType);
    }

    @Override
    public Flux<S3Entry> get(String prefix) {
        return s3Repository.get(prefix)
                .filter(contentTypeFilter)
                .map(s3Entry -> processor.process(s3Entry));
    }

    public static S3Repository wrap(S3Repository s3Repository) {
        return new MetaDataProcessingRepository(s3Repository);
    }
}
