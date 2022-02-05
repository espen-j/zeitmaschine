package io.zeitmaschine.s3;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;

import reactor.core.publisher.Sinks;

public class Processor {

    private final static Logger log = LoggerFactory.getLogger(Processor.class.getName());

    private final Sinks.Many<S3Entry> publisher;

    private S3Entry entry;

    public Processor(S3Entry entry) {
        this.entry = entry;
        this.publisher = Sinks
                .many()
                .unicast()
                .onBackpressureBuffer();
    }

    public static Processor from(S3Entry entry) {
        return new Processor(entry);
    }

    public S3Entry process(Map<String, String> metaData) {
        String version = metaData.get("zm-version");
        S3Entry.Builder builder = S3Entry.Builder.from(entry);

        if (version != null && version.equals("1")) {
            if (metaData.containsKey("zm-location-lon") && metaData.containsKey("zm-location-lat")) {
                String lon = metaData.get("zm-location-lon");
                String lat = metaData.get("zm-location-lat");

                builder.location(Long.parseLong(lon), Long.parseLong(lat));
            }
            Optional.ofNullable(metaData.get("zm-creation-date"))
                    .ifPresent(value -> {
                        try {
                            Date created = Date.from(Instant.ofEpochSecond(Long.parseLong(value)));
                            builder.created(created);
                        } catch (Exception e) {
                            log.error("Error parsing date from meta data.");
                        }
                    });
            this.entry = builder.build();
        } else {
            // exif extraction
            try (InputStream inputStream = entry.resourceSupplier().get().getInputStream()) {
                Metadata metadata = ImageMetadataReader.readMetadata(inputStream);

                // Nullables
                extractLocation(metadata).ifPresent(location -> builder.location(location.lon(), location.lat()));
                extractCreationDate(metadata).ifPresent(date -> builder.created(date));

                this.entry = builder.build();

                // TODO: update metadata
                publisher.tryEmitNext(entry);

            } catch (IOException | ImageProcessingException e) {
                log.error("Exif Metadata failed failed.", e);
            }
        }
        return entry;
    }

    private Optional<S3Entry.Location> extractLocation(Metadata metadata) {
        GpsDirectory gpsDirectory = metadata.getFirstDirectoryOfType(GpsDirectory.class);
        if (gpsDirectory != null) {
            GeoLocation loc = gpsDirectory.getGeoLocation();
            if (loc != null) {
                return Optional.of(new S3Entry.Location(loc.getLongitude(), loc.getLatitude()));
            }
        };
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
