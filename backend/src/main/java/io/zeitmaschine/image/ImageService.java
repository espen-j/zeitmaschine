package io.zeitmaschine.image;

import io.zeitmaschine.s3.S3Config;
import io.zeitmaschine.s3.S3Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.file.Paths;

@Service
public class ImageService {
    private final static Logger LOG = LoggerFactory.getLogger(ImageService.class.getName());
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
                        .switchIfEmpty(Mono.error(new RuntimeException(String.format("Resource found for '%s'.", key))))
                        .flatMap(res -> operationService.resize(res, dimension))
                        .doOnSuccess(res -> cache(key, res, dimension))));
    }

    private void cache(String key, Resource thumbnail, Dimension dimension) {
        try {
            s3Repository.put(cacheBucket, getThumbName(key, dimension), thumbnail, MediaType.IMAGE_JPEG_VALUE);
        } catch (Exception e) {
            LOG.error("Failed to store scaled image to cache.", e);
        }
    }

    private static String getThumbName(String key, Dimension dimension) {
        return Paths.get(dimension.name(), key).toString();
    }

    private Mono<Resource> loadCached(String key, Dimension dimension) {
        return s3Repository.get(cacheBucket, getThumbName(key, dimension));
    }
}
