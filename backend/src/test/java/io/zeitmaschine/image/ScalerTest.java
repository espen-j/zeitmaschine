package io.zeitmaschine.image;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.twelvemonkeys.image.AffineTransformOp;
import com.twelvemonkeys.image.ResampleOp;
import org.imgscalr.Scalr;
import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Ignore("Only for benchmarking purposes")
class ScalerTest {

    private Path outputDirectory;
    private BufferedImage input;
    private String originalImage;

    private int width = Dimension.SMALL.getSize();
    private int height = Dimension.SMALL.getSize();

    private String[] images = {"IMG_20161208_024708.jpg", "IMG_20180614_214734.jpg"};

    @BeforeEach
    void setUp() throws Exception {
        this.originalImage = "IMG_20181001_185137.jpg";
        InputStream stream = ClassLoader.getSystemResourceAsStream("images/" + originalImage);

        this.input = ImageIO.read(stream);

        int imgWidth = input.getWidth();
        int imgHeight = input.getHeight();



        if (imgWidth * height < imgHeight * width) {
            width = imgWidth * height / imgHeight;
        } else {
            height = imgHeight * width / imgWidth;
        }

        outputDirectory = Paths.get("/Users/espen/temp/image-test");
        if(!Files.exists(outputDirectory)) {
            Files.createDirectory(outputDirectory);
        }
    }

    @Test
    void basic() throws IOException {

        Path output = outputDirectory.resolve("basic_" + originalImage);
        if (!Files.exists(output)) {
            output = Files.createFile(output);
        }

        long before = System.currentTimeMillis();

        BufferedImage image = basic(input, width, height);
        long after = System.currentTimeMillis();

        long time = after - before;

        System.out.println("basic took ms: " + time);

        ImageIO.write(image, "jpg", output.toFile());
    }

    @Test
    void twelve() throws IOException {

        String oName = "IMG_20180614_214734.jpg";

        Path output = outputDirectory.resolve("twelve_" + oName);
        if (!Files.exists(output)) {
            output = Files.createFile(output);
        }

        long before = System.currentTimeMillis();

        BufferedImageOp resampler = new ResampleOp(width, height, ResampleOp.FILTER_LANCZOS); // A good default filter, see class documentation for more info
        BufferedImage image = resampler.filter(input, null);

        long after = System.currentTimeMillis();

        long time = after - before;
        System.out.println("Twelve took ms: " + time);

        ImageIO.write(image, "jpg", output.toFile());
    }


    @Test
    void progressive() throws IOException {

        String oName = "IMG_20181001_185137.jpg";

        Path output = outputDirectory.resolve("progressive_" + oName);
        if (!Files.exists(output)) {
            output = Files.createFile(output);
        }

        long before = System.currentTimeMillis();
        BufferedImage image = progressiveScaling(input, width, height);
        long after = System.currentTimeMillis();

        long time = after - before;
        System.out.println("Progressive took ms: " + time);

        ImageIO.write(image, "jpg", output.toFile());
    }


    @Test
    void scalrProgressive() throws IOException {

        String oName = "IMG_20181001_185137.jpg";

        Path output = outputDirectory.resolve("scalr_progressive_" + oName);
        if (!Files.exists(output)) {
            output = Files.createFile(output);
        }

        long before = System.currentTimeMillis();
        BufferedImage image = Scalr.resize(input, Scalr.Method.ULTRA_QUALITY, Scalr.Mode.FIT_EXACT, width, height);
        long after = System.currentTimeMillis();

        long time = after - before;
        System.out.println("Scalr Progressive took ms: " + time);

        ImageIO.write(image, "jpg", output.toFile());
    }

    @Test
    void laszlo() throws IOException {

        String oName = "IMG_20180614_214734.jpg";

        Path output = outputDirectory.resolve("laszlo_" + oName);
        if (!Files.exists(output)) {
            output = Files.createFile(output);
        }

        long before = System.currentTimeMillis();

        BufferedImage image = Scaler.scale(input, Dimension.SMALL);

        long after = System.currentTimeMillis();

        long time = after - before;
        System.out.println("Laszlo took ms: " + time);

        ImageIO.write(image, "jpg", output.toFile());
    }

    @Test
    void laszloOriented() throws IOException, ImageProcessingException, MetadataException {

        String oName = "IMG_20181001_185137.jpg";

        Path output = outputDirectory.resolve("laszloOriented_" + oName);
        if (!Files.exists(output)) {
            output = Files.createFile(output);
        }

        long before = System.currentTimeMillis();


        Metadata metadata = ImageMetadataReader.readMetadata(ClassLoader.getSystemResourceAsStream("images/" + originalImage));

        // original an thumbnail?
        Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);

        int imgHeight = input.getHeight();
        int imgWidth = input.getWidth();
        int orientation = directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
        if (orientation > 1) {
            AffineTransform operation = getExifTransformation(imgHeight, imgWidth, orientation);
            this.input = transformImage(input, operation);
            imgWidth = input.getWidth();
            imgHeight = input.getHeight();
        }


        if (imgWidth * height < imgHeight * width) {
            width = imgWidth * height / imgHeight;
        } else {
            height = imgHeight * width / imgWidth;
        }

        BufferedImage image = Scaler.scale(input, Dimension.SMALL);

        long after = System.currentTimeMillis();

        long time = after - before;
        System.out.println("LaszloOriented took ms: " + time);

        ImageIO.write(image, "jpg", output.toFile());
    }


    private static BufferedImage basic(BufferedImage input, int width, int height) {
        BufferedImage scaled = new BufferedImage(width, height,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setBackground(Color.BLACK);
            g.clearRect(0, 0, width, height);
            g.drawImage(input, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return scaled;
    }

    private static BufferedImage progressiveScaling(BufferedImage before, int width, int height) {
        Integer longestSideLength = width > height ? width : height;
        if (before != null) {
            Integer w = before.getWidth();
            Integer h = before.getHeight();

            Double ratio = h > w ? longestSideLength.doubleValue() / h : longestSideLength.doubleValue() / w;

            //Multi Step Rescale operation
            //This technique is describen in Chris Campbell’s blog The Perils of Image.getScaledInstance(). As Chris mentions, when downscaling to something less than factor 0.5, you get the best result by doing multiple downscaling with a minimum factor of 0.5 (in other words: each scaling operation should scale to maximum half the size).
            while (ratio < 0.5) {
                BufferedImage tmp = scale(before, 0.5);
                before = tmp;
                w = before.getWidth();
                h = before.getHeight();
                ratio = h > w ? longestSideLength.doubleValue() / h : longestSideLength.doubleValue() / w;
            }
            BufferedImage after = scale(before, ratio);
            return after;
        }
        return null;
    }

    private static BufferedImage scale(BufferedImage imageToScale, Double ratio) {
        Integer dWidth = ((Double) (imageToScale.getWidth() * ratio)).intValue();
        Integer dHeight = ((Double) (imageToScale.getHeight() * ratio)).intValue();
        BufferedImage scaledImage = new BufferedImage(dWidth, dHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics2D = scaledImage.createGraphics();
        graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics2D.drawImage(imageToScale, 0, 0, dWidth, dHeight, null);
        graphics2D.dispose();
        return scaledImage;
    }

    // Look at http://chunter.tistory.com/143 for information
    public static AffineTransform getExifTransformation(int height, int width, int orientation) {

        AffineTransform t = new AffineTransform();

        switch (orientation) {
            case 1:
                break;
            case 2: // Flip X
                t.scale(-1.0, 1.0);
                t.translate(-width, 0);
                break;
            case 3: // PI rotation
                t.translate(width, height);
                t.rotate(Math.PI);
                break;
            case 4: // Flip Y
                t.scale(1.0, -1.0);
                t.translate(0, -height);
                break;
            case 5: // - PI/2 and Flip X
                t.rotate(-Math.PI / 2);
                t.scale(-1.0, 1.0);
                break;
            case 6: // -PI/2 and -width
                t.translate(height, 0);
                t.rotate(Math.PI / 2);
                break;
            case 7: // PI/2 and Flip
                t.scale(-1.0, 1.0);
                t.translate(-height, 0);
                t.translate(0, width);
                t.rotate(  3 * Math.PI / 2);
                break;
            case 8: // PI / 2
                t.translate(0, width);
                t.rotate(  3 * Math.PI / 2);
                break;
        }

        return t;
    }

    public static BufferedImage transformImage(BufferedImage image, AffineTransform transform) {

        AffineTransformOp op = new AffineTransformOp(transform, AffineTransformOp.TYPE_BICUBIC);

        // Create an instance of the resulting image, with the same width, height and image type than the referenced one
        // TODO that's not true, need to switch the widthor height based e.g. for 90 degrees rotation (case 6)
        BufferedImage destinationImage = new BufferedImage( image.getHeight(), image.getWidth(), image.getType() );
        op.filter(image, destinationImage);

        // Set the created image as new buffered image
        return destinationImage;
    }
}