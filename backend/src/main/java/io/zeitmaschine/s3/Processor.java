package io.zeitmaschine.s3;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;

import io.zeitmaschine.index.Image;
import reactor.core.publisher.Mono;

public class Processor {

    public static Mono<Image> metaData(S3Entry s3Entry) {

        // FIXME contenttype not checked!
        try (InputStream inputStream = s3Entry.resourceSupplier().get().getInputStream()) {
            Image.Builder builder = Image.from(s3Entry.key());

            Metadata metadata = ImageMetadataReader.readMetadata(inputStream);
            Optional<ExifSubIFDDirectory> subIFDDirectory = Optional.ofNullable(metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class));
            subIFDDirectory.ifPresent(subIFD -> builder.createDate(subIFD.getDateOriginal()));

            Optional<GpsDirectory> gpsDirectory = Optional.ofNullable(metadata.getFirstDirectoryOfType(GpsDirectory.class));
            gpsDirectory.ifPresent(gps -> {
                GeoLocation geoLocation = gps.getGeoLocation();
                if (geoLocation != null)
                    builder.location(geoLocation.getLatitude(), geoLocation.getLongitude());
            });
            return Mono.just(builder.build());
        } catch (IOException | ImageProcessingException e) {
            return Mono.error(e);
        }
    }

}
