package io.zeitmaschine.s3;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

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

        publisher.asFlux().subscribe(subscriber);
    }

    public S3Entry process(S3Entry processing) {
        Map<String, String> metaData = processing.metaData();
        String version = metaData.get(META_VERSION);
        S3Entry.Builder builder = S3Entry.Builder.from(processing);

        S3Entry processed = processing;

        Map<String, String> processedMetaData = new HashMap<>(metaData);

        if (version != null && version.equals("1")) {
            if (metaData.containsKey(META_LOCATION_LON) && metaData.containsKey(META_LOCATION_LAT)) {
                String lon = metaData.get(META_LOCATION_LON);
                String lat = metaData.get(META_LOCATION_LAT);

                builder.location(Long.parseLong(lon), Long.parseLong(lat));
            }
            Optional.ofNullable(metaData.get(META_CREATION_DATE))
                    .ifPresent(value -> {
                        try {
                            Date created = Date.from(Instant.ofEpochSecond(Long.parseLong(value)));
                            builder.created(created);
                        } catch (Exception e) {
                            log.error("Error parsing date from meta data.");
                        }
                    });
            processed = builder
                    .metaData(processedMetaData)
                    .build();
        } else {
            // exif extraction
            try (InputStream inputStream = processing.resourceSupplier().get().getInputStream()) {
                Metadata metadata = ImageMetadataReader.readMetadata(inputStream);

                // Nullables
                extractLocation(metadata).ifPresent(location -> {
                    builder.location(location.lon(), location.lat());
                    processedMetaData.put(META_LOCATION_LON, String.valueOf(location.lon()));
                    processedMetaData.put(META_LOCATION_LAT, String.valueOf(location.lat()));
                });
                extractCreationDate(metadata).ifPresent(date -> {
                    builder.created(date);
                    processedMetaData.put(META_CREATION_DATE, String.valueOf(date.getTime()));
                });

                processedMetaData.put(META_VERSION, "1");
                processed = builder
                        .metaData(processedMetaData)
                        .build();

                // update metadata
                publisher.tryEmitNext(processed);

            } catch (IOException | ImageProcessingException e) {
                log.error("Exif Metadata failed failed.", e);
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
