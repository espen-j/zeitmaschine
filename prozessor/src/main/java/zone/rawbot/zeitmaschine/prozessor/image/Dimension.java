package zone.rawbot.zeitmaschine.prozessor.image;

public enum Dimension {

    THUMBNAIL(250), SMALL(1024), MEDIUM(2048), ORIGINAL;

    private int size;

    Dimension(int size) {
        this.size = size;
    }

    Dimension() {}

    public int getSize() {
        return size;
    }


    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }
}
