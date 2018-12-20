package zone.rawbot.zeitmaschine.prozessor.s3;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Date;

public class Image {

    private String name;
    private String thumbnail;
    private Date created;
    private Location location;

    public String getName() {
        return name;
    }

    public String getThumbnail() {
        return thumbnail;
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
        private final String thumbnail;
        private Location location;
        private Date created;

        private Builder(String name) {
            this.name = name;
            this.thumbnail = "http://localhost:8080/image/" + name;
        }

        public Builder location(double latitude, double longitude) {
            Location location = new Location();
            location.latitude = latitude;
            location.longitude = longitude;
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
            image.thumbnail = this.thumbnail;
            image.location = this.location;
            image.created = this.created;
            return image;
        }

    }

    public static class Location {

        private double latitude;
        private double longitude;

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }
    }
}
