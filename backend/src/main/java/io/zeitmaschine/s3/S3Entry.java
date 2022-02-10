package io.zeitmaschine.s3;

import java.util.Date;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.core.io.Resource;

public record S3Entry(String key, String contentType, long size, Supplier<Resource> resourceSupplier, Location location, Date created, Map<String, String> metaData) {

    public record Location(double lon, double lat) {}

    public static Builder builder(){
        return new Builder();
    }

    public static class Builder {
        private String key;
        private String contentType;
        private long size;
        private Supplier<Resource> resourceSupplier;
        private Location location;
        private Date created;
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
                    .created(entry.created())
                    .metaData(entry.metaData());
            if (entry.location() != null) {
                builder.location(entry.location().lon(), entry.location().lat());
            }
            return builder;
        }

        public Builder created(Date created) {
            this.created = created;
            return this;
        }

        public Builder location(double lon, double lat) {
            this.location = new Location(lon, lat);
            return this;
        }

        public S3Entry build() {
            return new S3Entry(key, contentType, size, resourceSupplier, location, created, metaData);
        }
    }
}
