package io.zeitmaschine.image;

import com.twelvemonkeys.image.ResampleOp;
import org.imgscalr.Scalr;
import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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

    private int width = Dimension.THUMBNAIL.getSize();
    private int height = Dimension.THUMBNAIL.getSize();

    private String[] images = {"IMG_20161208_024708.jpg", "IMG_20180614_214734.jpg"};

    @BeforeEach
    void setUp() throws IOException {
        this.originalImage = "IMG_20161208_024708.jpg";
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

        String oName = "IMG_20180614_214734.jpg";

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

        String oName = "IMG_20180614_214734.jpg";

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

        BufferedImage image = Scaler.scale(input, Dimension.THUMBNAIL);

        long after = System.currentTimeMillis();

        long time = after - before;
        System.out.println("Laszlo took ms: " + time);

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
}