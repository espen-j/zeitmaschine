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
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataFormatImpl;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.stream.Stream;

@Ignore("Only for benchmarking purposes")
class ScalerTest {

    private Path outputDirectory;
    private static final String[] images = {"IMG_20161208_024708.jpg", "IMG_20180614_214734.jpg", "IMG_20181001_185137.jpg"};

    @BeforeEach
    void setUp() throws Exception {

        this.outputDirectory = Paths.get("/Users/espen/temp/image-test");
        if (!Files.exists(outputDirectory)) {
            Files.createDirectory(outputDirectory);
        }
    }

    @ParameterizedTest
    @ArgumentsSource(TestImages.class)
    void basic(TestImage image) throws IOException {

        Path output = outputDirectory.resolve("basic_" + image.original);
        if (!Files.exists(output)) {
            output = Files.createFile(output);
        }
        long before = System.currentTimeMillis();

        BufferedImage oImage = basic(image.image, image.width, image.height);
        long after = System.currentTimeMillis();

        long time = after - before;

        System.out.println("basic took ms: " + time);

        ImageIO.write(oImage, "jpg", output.toFile());
    }

    @ParameterizedTest
    @ArgumentsSource(TestImages.class)
    void twelve(TestImage image) throws IOException {


        Path output = outputDirectory.resolve("twelve_" + image.original);
        if (!Files.exists(output)) {
            output = Files.createFile(output);
        }

        long before = System.currentTimeMillis();

        BufferedImageOp resampler = new ResampleOp(image.width, image.height, ResampleOp.FILTER_LANCZOS); // A good default filter, see class documentation for more info
        BufferedImage oImage = resampler.filter(image.image, null);

        long after = System.currentTimeMillis();

        long time = after - before;
        System.out.println("Twelve took ms: " + time);

        ImageIO.write(oImage, "jpg", output.toFile());
    }

    @ParameterizedTest
    @ArgumentsSource(TestImages.class)
    void progressive(TestImage image) throws IOException {

        Path output = outputDirectory.resolve("progressive_" + image.original);
        if (!Files.exists(output)) {
            output = Files.createFile(output);
        }

        long before = System.currentTimeMillis();
        BufferedImage oImage = progressiveScaling(image.image, image.width, image.height);
        long after = System.currentTimeMillis();

        long time = after - before;
        System.out.println("Progressive took ms: " + time);

        ImageIO.write(oImage, "jpg", output.toFile());
    }

    @ParameterizedTest
    @ArgumentsSource(TestImages.class)
    void scalrProgressive(TestImage image) throws IOException {

        Path output = outputDirectory.resolve("scalr_progressive_" + image.original);
        if (!Files.exists(output)) {
            output = Files.createFile(output);
        }

        long before = System.currentTimeMillis();
        BufferedImage oImage = Scalr.resize(image.image, Scalr.Method.ULTRA_QUALITY, Scalr.Mode.FIT_EXACT,image.width, image.height);
        long after = System.currentTimeMillis();

        long time = after - before;
        System.out.println("Scalr Progressive took ms: " + time);

        ImageIO.write(oImage, "jpg", output.toFile());
    }

    @ParameterizedTest
    @ArgumentsSource(TestImages.class)
    void lanczos(TestImage image) throws IOException {

        Path output = outputDirectory.resolve("lanczos_" + image.original);
        if (!Files.exists(output)) {
            output = Files.createFile(output);
        }

        long before = System.currentTimeMillis();

        BufferedImage oImage = Scaler.scale(image.image, Dimension.SMALL);

        long after = System.currentTimeMillis();

        long time = after - before;
        System.out.println("lanczos took ms: " + time);

        ImageIO.write(oImage, "jpg", output.toFile());
    }

    @ParameterizedTest
    @ArgumentsSource(TestImages.class)
    void lanczosOriented(TestImage image) throws IOException, ImageProcessingException, MetadataException {

        Path output = outputDirectory.resolve("lanczosOriented_" + image.original);
        if (!Files.exists(output)) {
            output = Files.createFile(output);
        }

        long before = System.currentTimeMillis();

        Metadata metadata = ImageMetadataReader.readMetadata(ClassLoader.getSystemResourceAsStream("images/" + image.original));

        // original an thumbnail?
        Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);

        if (directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
            int orientation = directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            if (orientation > 1) {
                AffineTransform operation = getExifTransformation(image.image.getHeight(), image.image.getWidth(), orientation);
                BufferedImage tImage = transformImage(image.image, operation);

                // assignments not really needed, except for reference to new image
                image.width = tImage.getWidth();
                image.height = tImage.getHeight();
                image.image = tImage;
            }
        }

        BufferedImage oImage = Scaler.scale(image.image, Dimension.SMALL);

        long after = System.currentTimeMillis();

        long time = after - before;
        System.out.println("lanczos took ms: " + time);

        ImageIO.write(oImage, "jpg", output.toFile());
    }

    @ParameterizedTest
    @ArgumentsSource(TestImages.class)
    void lanczosExif(TestImage image) throws IOException {

        Path output = outputDirectory.resolve("lanczos_exif_" + image.original);
        if (!Files.exists(output)) {
            output = Files.createFile(output);
        }

        long before = System.currentTimeMillis();


        InputStream iStream = ClassLoader.getSystemResourceAsStream("images/" + image.original);
        ImageInputStream stream = ImageIO.createImageInputStream(iStream);

        Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
        if (readers.hasNext())
        {
            ImageReader reader = readers.next();
            reader.setInput(stream);
            ImageReaderSpi spi = reader.getOriginatingProvider();

            if (spi.isStandardImageMetadataFormatSupported())
            {
                processFileWithReader(image.original, reader);
            }
        }


        BufferedImage oImage = Scaler.scale(image.image, Dimension.SMALL);

        long after = System.currentTimeMillis();

        long time = after - before;
        System.out.println("lanczos took ms: " + time);

        writeImage(output.toFile(), oImage, createMetadata());

    }

    private static void processFileWithReader(String file, ImageReader reader) throws IOException
    {
        ImageInputStream stream = null;

        try
        {

            InputStream iStream = ClassLoader.getSystemResourceAsStream("images/" + file);
            stream = ImageIO.createImageInputStream(iStream);

            reader.setInput(stream, true);

            IIOMetadata metadata = reader.getImageMetadata(0);

            Node root = metadata.getAsTree(IIOMetadataFormatImpl.standardMetadataFormatName);
            Node dimension = getChildElement(root, "Dimension");
            if (dimension != null) {
                Node orientation = getChildElement(dimension, "ImageOrientation");
                orientation.getAttributes();

            }
            dumpTreeStructure(root);
        }
        finally
        {
            if (stream != null)
            {
                stream.close();
            }
        }
    }


    private static Element getChildElement(Node parent, String name)
    {
        NodeList children = parent.getChildNodes();
        int count = children.getLength();
        for (int i = 0; i < count; i++)
        {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE)
            {
                if (child.getNodeName().equals(name))
                {
                    return (Element)child;
                }
            }
        }

        return null;
    }

    private static void dumpTreeStructure(Node parent)
    {
        NodeList children = parent.getChildNodes();
        int count = children.getLength();
        for (int i = 0; i < count; i++)
        {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE)
            {
                System.out.println(parent.getNodeName() + " -> " + child.getNodeName());
                if (child.hasChildNodes()) {
                    dumpTreeStructure(child);
                }
            }
        }

    }

    private static void writeImage(File outputFile, BufferedImage image, IIOMetadataNode newMetadata) throws IOException
    {
        String extension = "jpg";
        ImageTypeSpecifier imageType = ImageTypeSpecifier.createFromBufferedImageType(image.getType());

        ImageOutputStream stream = null;
        try
        {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersBySuffix(extension);
            while(writers.hasNext())
            {
                ImageWriter writer = writers.next();
                ImageWriteParam writeParam = writer.getDefaultWriteParam();
                IIOMetadata imageMetadata = writer.getDefaultImageMetadata(imageType, writeParam);
                if (!imageMetadata.isStandardMetadataFormatSupported())
                {
                    continue;
                }
                if (imageMetadata.isReadOnly())
                {
                    continue;
                }

                //imageMetadata.mergeTree(IIOMetadataFormatImpl.standardMetadataFormatName, newMetadata);

                IIOImage imageWithMetadata = new IIOImage(image, null, imageMetadata);

                stream = ImageIO.createImageOutputStream(outputFile);
                writer.setOutput(stream);
                writer.write(null, imageWithMetadata, writeParam);
                break;
            }
        }
        finally
        {
            if (stream != null)
            {
                stream.close();
            }
        }
    }

    private static IIOMetadataNode createMetadata()
    {

        IIOMetadataNode orientation = new IIOMetadataNode("Orientation");
        orientation.setAttribute("value", "6");


        IIOMetadataNode root = new IIOMetadataNode(IIOMetadataFormatImpl.standardMetadataFormatName);
        root.appendChild(orientation);

        return root;
    }


    private static TestImage loadImage(String imageName) throws RuntimeException {
        int width = Dimension.SMALL.getSize();
        int height = Dimension.SMALL.getSize();

        InputStream stream = ClassLoader.getSystemResourceAsStream("images/" + imageName);

        try {
            BufferedImage image = ImageIO.read(stream);
            int imgWidth = image.getWidth();
            int imgHeight = image.getHeight();

            // set height or width to ficed size and recalculate the other
            if (imgWidth * height < imgHeight * width) {
                width = imgWidth * height / imgHeight;
            } else {
                height = imgHeight * width / imgWidth;
            }
            TestImage test = new TestImage();
            test.original = imageName;
            test.width = width;
            test.height = height;
            test.image = image;
            return test;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
    private static AffineTransform getExifTransformation(int height, int width, int orientation) {

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
                t.rotate(3 * Math.PI / 2);
                break;
            case 8: // PI / 2
                t.translate(0, width);
                t.rotate(3 * Math.PI / 2);
                break;
        }

        return t;
    }

    private static BufferedImage transformImage(BufferedImage image, AffineTransform transform) {

        AffineTransformOp op = new AffineTransformOp(transform, AffineTransformOp.TYPE_BICUBIC);

        // Create an instance of the resulting image, with the same width, height and image type than the referenced one
        // TODO that's not true, need to switch the widthor height based e.g. for 90 degrees rotation (case 6)
        BufferedImage destinationImage = new BufferedImage(image.getHeight(), image.getWidth(), image.getType());
        op.filter(image, destinationImage);

        // Set the created image as new buffered image
        return destinationImage;
    }

    private static class TestImage {
        private BufferedImage image;
        private String original;
        private int width;
        private int height;
    }

    private static class TestImages implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) throws Exception {
            return Stream.of(images).map(image -> loadImage(image)).map(Arguments::of);
        }
    }
}