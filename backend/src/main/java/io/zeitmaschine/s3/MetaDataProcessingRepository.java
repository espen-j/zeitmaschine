package io.zeitmaschine.s3;

import java.util.Map;

import org.springframework.core.io.Resource;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MetaDataProcessingRepository implements S3Repository {

    private S3Repository s3Repository;
    private Processor processor;

    MetaDataProcessingRepository(S3Repository s3Repository) {
        this.s3Repository = s3Repository;
        this.processor = new Processor(s3Entry -> metaData(s3Entry.key(), s3Entry.metaData()));
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
                .map(s3Entry -> processor.process(s3Entry));
    }

    @Override
    public void put(String bucket, String key, Resource resource, String contentType) {
        s3Repository.put(bucket, key, resource, contentType);
    }

    @Override
    public void metaData(String key, Map<String, String> metaData) {
        s3Repository.metaData(key, metaData);
    }

    @Override
    public Flux<S3Entry> get(String prefix) {
        return s3Repository.get(prefix)
                .map(s3Entry -> processor.process(s3Entry));
    }

    public static S3Repository wrap(S3Repository s3Repository) {
        return new MetaDataProcessingRepository(s3Repository);
    }
}
