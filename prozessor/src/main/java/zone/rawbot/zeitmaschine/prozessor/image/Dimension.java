package zone.rawbot.zeitmaschine.prozessor.image;

public enum Dimension {

    THUMBNAIL(250), MEDIUM(1024), BIG(2048);

    private final int size;

    Dimension(int size) {
        this.size = size;
    }

    public int getSize() {
        return size;
    }
}
