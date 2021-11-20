package io.zeitmaschine.s3;

import org.springframework.core.io.Resource;

public record S3Entry(String key, Resource resource) {

    public static S3Entry of(String key, Resource resource) {
        return new S3Entry(key, resource);
    }
}
