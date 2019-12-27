package io.zeitmaschine.image;

import io.zeitmaschine.s3.S3Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.file.Paths;

import static io.zeitmaschine.s3.S3Repository.BUCKET_CACHE_NAME;
import static io.zeitmaschine.s3.S3Repository.BUCKET_NAME;

@Service
public class ImageService {
    private final static Logger LOG = LoggerFactory.getLogger(ImageService.class.getName());

    private S3Repository s3Repository;
    private ImageOperationService operationService;

    @Autowired
    public ImageService(S3Repository s3Repository, ImageOperationService operationService) {
        this.s3Repository = s3Repository;
        this.operationService = operationService;
    }

    public Mono<Resource> getImageByDimension(String key, Dimension dimension) {
        return loadCached(key, dimension).switchIfEmpty(Mono.defer(() ->
                s3Repository.get(BUCKET_NAME, key)
                        .flatMap(res -> operationService.resize(res, dimension))
                        .doOnSuccess(res -> cache(key, res, dimension))));
    }

    private void cache(String key, Resource thumbnail, Dimension dimension) {
        try {
            s3Repository.put(BUCKET_CACHE_NAME, getThumbName(key, dimension), thumbnail, MediaType.IMAGE_JPEG_VALUE);
        } catch (Exception e) {
            LOG.error("Failed to store scaled image to cache.", e);
        }
    }

    private static String getThumbName(String key, Dimension dimension) {
        return Paths.get(dimension.name(), key).toString();
    }

    private Mono<Resource> loadCached(String key, Dimension dimension) {
        return s3Repository.get(BUCKET_CACHE_NAME, getThumbName(key, dimension));
    }
}
