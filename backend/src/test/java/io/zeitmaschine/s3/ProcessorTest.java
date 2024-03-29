package io.zeitmaschine.s3;

import static io.zeitmaschine.s3.Processor.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;

class ProcessorTest {

    @Test
    void exifCreatedDate() {
        // GIVEN

        Map metaData = Map.of();

        Processor processor = new Processor(s3Entry -> {});

        // Image with created date, but NO location in Exif data
        ClassPathResource image = new ClassPathResource("images/IMG_20181001_185137.jpg");
        S3Entry entry = S3Entry.builder()
                .key("test")
                .contentType(MediaType.IMAGE_JPEG_VALUE)
                .size(1024)
                .metaData(metaData)
                .resourceSupplier(() -> image)
                .build();

        S3Entry processed = processor.process(entry);

        assertThat(processed.created(), notNullValue());
        assertThat(processed.location(), nullValue());
    }

    @Test
    void exifLocation() {
        // GIVEN

        Map metaData = Map.of();
        Processor processor = new Processor(s3Entry -> {});


        // Image with created date AND location in Exif data
        ClassPathResource image = new ClassPathResource("images/PXL_20220202_160830986.MP.jpg");
        S3Entry entry = S3Entry.builder()
                .key("test")
                .contentType(MediaType.IMAGE_JPEG_VALUE)
                .size(1024)
                .metaData(metaData)
                .resourceSupplier(() -> image)
                .build();

        S3Entry processed = processor.process(entry);

        assertThat(processed.created(), notNullValue());
        assertThat(processed.location(), notNullValue());
    }

    @Test
    void allFromMetaData() {
        // GIVEN
        Date now = Date.from(Instant.now());
        Map<String, String> metaData = Map.of(
                META_VERSION, String.valueOf(1),
                META_CREATION_DATE, String.valueOf(now.getTime()),
                META_LOCATION_LON, "123",
                META_LOCATION_LAT, "321"
        );

        Processor processor = new Processor(s3Entry -> {});

        S3Entry entry = S3Entry.builder()
                .key("test")
                .contentType(MediaType.IMAGE_JPEG_VALUE)
                .size(1024)
                .metaData(metaData)
                .resourceSupplier(() -> {
                    throw new RuntimeException("Should not fetch binary for exif data!");
                })
                .build();

        S3Entry processed = processor.process(entry);

        assertTrue(now.equals(processed.created()));
        assertThat(processed.location(), notNullValue());
    }

    @Test
    void exifDataPublished() {
        // GIVEN

        Map metaData = Map.of();

        List<S3Entry> recorded = Lists.newArrayList();
        Processor processor = new Processor(s3Entry -> recorded.add(s3Entry));

        // Image with created date AND location in Exif data
        ClassPathResource image = new ClassPathResource("images/PXL_20220202_160830986.MP.jpg");
        S3Entry entry = S3Entry.builder()
                .key("test")
                .contentType(MediaType.IMAGE_JPEG_VALUE)
                .size(1024)
                .metaData(metaData)
                .resourceSupplier(() -> image)
                .build();

        S3Entry processed = processor.process(entry);

        assertThat(processed.created(), notNullValue());
        assertThat(processed.location(), notNullValue());
        assertTrue(recorded.get(0).key().equals("test"));
    }

    @Test
    void contentType() {
        // GIVEN

        Map metaData = Map.of();

        List<S3Entry> recorded = Lists.newArrayList();
        Processor processor = new Processor(s3Entry -> recorded.add(s3Entry));

        // Image with created date AND location in Exif data
        ClassPathResource image = new ClassPathResource("images/PXL_20220202_160830986.MP.jpg");
        S3Entry entry = S3Entry.builder()
                .key("test")
                .contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .size(1024)
                .metaData(metaData)
                .resourceSupplier(() -> image)
                .build();

        S3Entry processed = processor.process(entry);

        assertThat(processed.created(), notNullValue());
        assertThat(processed.location(), notNullValue());
        assertTrue(processed.contentType().equals(MediaType.IMAGE_JPEG_VALUE));
    }
}