package io.zeitmaschine.s3;

import static io.zeitmaschine.s3.Processor.*;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.core.io.Resource;

public record S3Entry(String key, String contentType, long size, Supplier<Resource> resourceSupplier, Map<String, String> metaData) {

    public static Builder builder(){
        return new Builder();
    }

    public Date created() {
        String date = metaData.get(META_CREATION_DATE);
        if (date != null) {
            try {
                return Date.from(Instant.ofEpochSecond(Long.parseLong(date)));
            } catch (Exception e) {
                // ??
                // log.error("Error parsing date from meta data.");
            }
        }
        return null;
    }

    public Location location() {
        String lon = metaData.get(META_LOCATION_LON);
        String lat = metaData.get(META_LOCATION_LON);
        if (lon != null && lat != null) {
            return new Location(Double.parseDouble(lon), Double.parseDouble(lat));
        }
        return null;
    }

    public static class Builder {
        private String key;
        private String contentType;
        private long size;
        private Supplier<Resource> resourceSupplier;
        private Map<String, String> metaData = Map.of();

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder size(long size) {
            this.size = size;
            return this;
        }

        public Builder resourceSupplier(Supplier<Resource> resourceSupplier) {
            this.resourceSupplier = resourceSupplier;
            return this;
        }

        public Builder metaData(Map<String, String> metaData) {
            this.metaData = metaData;
            return this;
        }

        public static Builder from(S3Entry entry) {
            Builder builder = builder()
                    .key(entry.key())
                    .size(entry.size())
                    .contentType(entry.contentType())
                    // Does this work?!
                    .resourceSupplier(entry.resourceSupplier())
                    .metaData(entry.metaData());
            return builder;
        }

        public S3Entry build() {
            return new S3Entry(key, contentType, size, resourceSupplier, metaData);
        }
    }

    public record Location (double lon, double lat) {}
}
