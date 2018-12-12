package zone.rawbot.zeitmaschine.prozessor.s3;

public class Image {

    public String getName() {
        return name;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    private String name;
    private String thumbnail;

    public static Image from(String name, String thumbnail) {
        Image image = new Image();
        image.name = name;
        image.thumbnail = "http://localhost:8080/image/" + thumbnail;
        return image;
    }
}
