package io.zeitmaschine.index;

import java.util.List;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jayway.jsonpath.JsonPath;

import io.zeitmaschine.s3.S3Config;
import io.zeitmaschine.s3.S3Entry;
import io.zeitmaschine.s3.S3Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Index endpoint for s3 to notify and trigger a manual reindexing.
 */
@RestController
@RequestMapping("/index")
public class IndexEndpoint {

    private final static Logger LOG = LoggerFactory.getLogger(IndexEndpoint.class.getName());

    private final S3Repository repository;
    private final Indexer indexer;
    private final String bucket;
    private final static Predicate<S3Entry> contentTypeFilter = s3Entry -> s3Entry.contentType().equals(MediaType.IMAGE_JPEG_VALUE);

    @Autowired
    public IndexEndpoint(S3Repository repository, S3Config config, Indexer indexer) {
        this.repository = repository;
        this.indexer = indexer;
        this.bucket = config.getBucket();
    }

    // TODO: publish to stream
    @PostMapping("/webhook")
    public ResponseEntity<Void> notify(@RequestBody String json) {

        List<String> keys = JsonPath.read(json, "$.Records[*].s3.object.key");

        Flux.fromIterable(keys)
                .flatMap(key -> repository.get(bucket, key))
                .filter(contentTypeFilter)
                .flatMap(entry -> indexer.toImage(entry)
                        .doOnError(ex -> LOG.error("Failed to process image.", ex))
                        .onErrorResume(ex -> Mono.empty()))
                .subscribe(image -> indexer.index(image));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/prefix")
    public ResponseEntity<Void> prefix(@RequestBody String json) {
        String prefix = JsonPath.read(json, "$.prefix");
        LOG.info("Indexing objects with prefix '{}'.", prefix);
        repository.get(prefix)
                .filter(contentTypeFilter)
                .flatMap(entry -> indexer.toImage(entry)
                        .doOnError(ex -> LOG.error("Failed to process image.", ex))
                        .onErrorResume(ex -> Mono.empty()))
                .subscribe(image -> indexer.index(image));

        return ResponseEntity.ok().build();
    }

    @PostMapping("/init")
    public ResponseEntity<Void> index() {
        indexer.initIndex();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/wipe")
    public ResponseEntity<Void> wipe() {
        indexer.wipe();
        return ResponseEntity.ok().build();
    }
}
