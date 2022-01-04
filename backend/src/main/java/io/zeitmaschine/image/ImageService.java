package io.zeitmaschine.image;

import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import io.zeitmaschine.s3.S3Config;
import io.zeitmaschine.s3.S3Repository;
import reactor.core.publisher.Mono;

@Service
public class ImageService {
    private final String bucket;
    private final String cacheBucket;

    private S3Repository s3Repository;
    private ImageOperationService operationService;

    @Autowired
    public ImageService(S3Repository s3Repository, S3Config config, ImageOperationService operationService) {
        this.s3Repository = s3Repository;
        this.operationService = operationService;
        this.bucket = config.getBucket();
        this.cacheBucket = config.getCacheBucket();
    }

    public Mono<Resource> getImageByDimension(String key, Dimension dimension) {
        return loadCached(key, dimension).switchIfEmpty(Mono.defer(() ->
                s3Repository.get(bucket, key)
                        // https://stackoverflow.com/questions/53595420/correct-way-of-throwing-exceptions-with-reactor
                        .switchIfEmpty(Mono.error(new RuntimeException(String.format("Resource not found '%s'.", key))))
                        .flatMap(entry -> operationService.resize(entry.resourceSupplier().get(), dimension))
                        .doOnSuccess(res -> cache(key, res, dimension))));
    }

    private void cache(String key, Resource thumbnail, Dimension dimension) {
        s3Repository.put(cacheBucket, getThumbName(key, dimension), thumbnail, MediaType.IMAGE_JPEG_VALUE);
    }

    private static String getThumbName(String key, Dimension dimension) {
        return Paths.get(dimension.name(), key).toString();
    }

    private Mono<Resource> loadCached(String key, Dimension dimension) {
        return s3Repository.get(cacheBucket, getThumbName(key, dimension))
                .map(entry -> entry.resourceSupplier().get());
    }
}
