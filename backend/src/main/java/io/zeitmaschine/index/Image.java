package io.zeitmaschine.index;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Date;

import io.zeitmaschine.s3.S3Entry;

public class Image {

    private String name;
    private Date created;
    private Location location;

    public String getName() {
        return name;
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern ="yyyy-MM-dd'T'HH:mm:ss.SSSZZ")
    public Date getCreated() {
        return created;
    }

    public Location getLocation() {
        return location;
    }

    public static Builder from(String name) {
        return new Builder(name);
    }

    public static class Builder {

        private final String name;
        private Location location;
        private Date created;

        private Builder(String name) {
            this.name = name;
        }

        public Builder location(double latitude, double longitude) {
            Location location = new Location();
            location.lat = latitude;
            location.lon = longitude;
            this.location = location;
            return this;
        }

        public Builder createDate(Date created) {
            this.created = created;
            return this;
        }

        public Image build() {
            Image image = new Image();
            image.name = this.name;
            image.location = this.location;
            image.created = this.created;
            return image;
        }

        public Builder location(S3Entry.Location location) {
            if (location != null) {
                Location loc = new Location();
                loc.lat = location.lat();
                loc.lon = location.lon();
                this.location = loc;
            }
            return this;
        }
    }

    public static class Location {

        private double lat;
        private double lon;

        public double getLat() {
            return lat;
        }

        public double getLon() {
            return lon;
        }
    }
}
