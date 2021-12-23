package io.zeitmaschine.s3;

import java.util.function.Supplier;

import org.springframework.core.io.Resource;

public record S3Entry(String key, long size, Supplier<Resource> resourceSupplier) {

    public static S3Entry of(String key, long size,  Supplier<Resource> resourceSupplier) {
        return new S3Entry(key, size, resourceSupplier);
    }
}
