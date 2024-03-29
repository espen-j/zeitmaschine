package io.zeitmaschine.s3;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

import com.drew.imaging.FileType;
import com.drew.imaging.FileTypeDetector;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;

import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

public class Processor {

    private final static Logger log = LoggerFactory.getLogger(Processor.class.getName());

    public static final String META_VERSION_CURRENT = "1";
    public static final String META_VERSION = "zm-meta-version";
    public static final String META_LOCATION_LON = "zm-location-lon";
    public static final String META_LOCATION_LAT = "zm-location-lat";
    public static final String META_CREATION_DATE = "zm-creation-date";

    final Sinks.Many<S3Entry> publisher;

    public Processor(Consumer<S3Entry> subscriber) {
        this.publisher = Sinks
                .many()
                .unicast()
                .onBackpressureBuffer();

        publisher.asFlux()
                .publishOn(Schedulers.boundedElastic())
                .subscribe(subscriber);
    }

    public S3Entry process(S3Entry processing) {
        Map<String, String> metaData = processing.metaData();
        String version = metaData.get(META_VERSION);

        S3Entry processed = processing;

        if (version != null) {
            log.debug("S3Entry already processed '{}', skipping..", processing.key());
        } else {
            Map<String, String> processedMetaData = new HashMap<>(metaData);

            String contentType = processing.contentType();
            // exif extraction
            try (BufferedInputStream inputStream = new BufferedInputStream(processing.resourceSupplier().get().getInputStream())) {
                // BufferedInputStream needed for FileTypeDetector#detectFileType
                if (contentType.equals(MediaType.APPLICATION_OCTET_STREAM_VALUE) || contentType.equals(MinioRepository.UNKNOWN_CONTENT_TYPE)) {
                    // contentType extraction
                    FileType fileType = FileTypeDetector.detectFileType(inputStream);

                    // Allows re-reading the inputstream - hmm..
                    inputStream.reset();
                    if (fileType == FileType.Jpeg) {
                        contentType = MediaType.IMAGE_JPEG_VALUE;
                    } else if (fileType == FileType.Mp4) {
                        // TODO "video/mp4" - but needs frontend filtering
                    }
                }

                Metadata metadata = ImageMetadataReader.readMetadata(inputStream);

                // Nullables
                extractLocation(metadata).ifPresent(location -> {
                    processedMetaData.put(META_LOCATION_LON, String.valueOf(location.lon()));
                    processedMetaData.put(META_LOCATION_LAT, String.valueOf(location.lat()));
                });
                extractCreationDate(metadata).ifPresent(date -> {
                    processedMetaData.put(META_CREATION_DATE, String.valueOf(date.getTime()));
                });

                processedMetaData.put(META_VERSION, META_VERSION_CURRENT);

                // update metadata
                processed = S3Entry.Builder.from(processing)
                        .contentType(contentType)
                        .metaData(processedMetaData)
                        .build();

                // Update
                publisher.tryEmitNext(processed);
            } catch (IOException | ImageProcessingException e) {
                log.error("Failed to read metadata from file '{}'.", processing.key(), e);
            }
        }
        return processed;
    }

    private Optional<S3Entry.Location> extractLocation(Metadata metadata) {
        GpsDirectory gpsDirectory = metadata.getFirstDirectoryOfType(GpsDirectory.class);
        if (gpsDirectory != null) {
            GeoLocation loc = gpsDirectory.getGeoLocation();
            if (loc != null) {
                return Optional.of(new S3Entry.Location(loc.getLongitude(), loc.getLatitude()));
            }
        }
        return Optional.empty();
    }

    private Optional<Date> extractCreationDate(Metadata metadata) {
        Optional<ExifSubIFDDirectory> subIFDDirectory = Optional.ofNullable(metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class));
        if (subIFDDirectory.isPresent()) {
            return Optional.ofNullable(subIFDDirectory.get().getDateOriginal());
        }
        return Optional.empty();
    }
}
