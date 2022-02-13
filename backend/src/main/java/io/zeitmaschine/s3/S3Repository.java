package io.zeitmaschine.s3;

import java.util.Map;

import org.springframework.core.io.Resource;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface S3Repository {
    boolean health();

    void initBucket();

    Mono<S3Entry> get(String bucket, String key);

    void put(String bucket, String key, Resource resource, String contentType);

    void metaData(String key, Map<String, String> metaData, String contentType);

    Flux<S3Entry> get(String prefix);
}
