package io.zeitmaschine.s3;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;

class ProcessorTest {

    @Test
    void exifCreatedDate() {
        // GIVEN

        Map metaData = Map.of();

        // Image with created date, but NO location in Exif data
        ClassPathResource image = new ClassPathResource("images/IMG_20181001_185137.jpg");
        S3Entry entry = S3Entry.builder()
                .key("test")
                .contentType(MediaType.IMAGE_JPEG_VALUE)
                .size(1024)
                .resourceSupplier(() -> image)
                .build();

        S3Entry processed = Processor.from(entry).process(metaData);

        assertThat(processed.created(), notNullValue());
        assertThat(processed.location(), nullValue());
    }

    @Test
    void exifLocation() {
        // GIVEN

        Map metaData = Map.of();

        // Image with created date AND location in Exif data
        ClassPathResource image = new ClassPathResource("images/PXL_20220202_160830986.MP.jpg");
        S3Entry entry = S3Entry.builder()
                .key("test")
                .contentType(MediaType.IMAGE_JPEG_VALUE)
                .size(1024)
                .resourceSupplier(() -> image)
                .build();

        S3Entry processed = Processor.from(entry).process(metaData);

        assertThat(processed.created(), notNullValue());
        assertThat(processed.location(), notNullValue());
    }

    @Test
    void allFromMetaData() {
        // GIVEN
        Map<String, String> metaData = Map.of(
                "zm-version", String.valueOf(1),
                "zm-creation-date", String.valueOf(Date.from(Instant.now()).getTime()),
                "zm-location-lon", "123",
                "zm-location-lat", "321"
        );

        S3Entry entry = S3Entry.builder()
                .key("test")
                .contentType(MediaType.IMAGE_JPEG_VALUE)
                .size(1024)
                .resourceSupplier(() -> {
                    throw new RuntimeException("Should not fetch binary for exif data!");
                })
                .build();

        S3Entry processed = Processor.from(entry).process(metaData);

        assertThat(processed.created(), greaterThanOrEqualTo(Date.from(Instant.now().minusSeconds(TimeUnit.MINUTES.toSeconds(1)))));
        assertThat(processed.location(), notNullValue());
    }
}