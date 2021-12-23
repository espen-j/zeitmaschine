package io.zeitmaschine.index;

import com.jayway.jsonpath.JsonPath;

import io.zeitmaschine.s3.S3Config;
import io.zeitmaschine.s3.S3Repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

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

    @Autowired
    public IndexEndpoint(S3Repository repository, S3Config config, Indexer indexer) {
        this.repository = repository;
        this.indexer = indexer;
        this.bucket = config.getBucket();
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> notify(@RequestBody String json) {

        List<String> keys = JsonPath.read(json, "$.Records[*].s3.object.key");

        Flux.fromIterable(keys)
                .flatMap(key -> repository.get(bucket, key)
                            .map(entry -> indexer.toImage(entry.key(), entry.resourceSupplier().get())))
                .subscribe(image -> indexer.index(image));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/prefix")
    public ResponseEntity<Void> prefix(@RequestBody String json) {
        String prefix = JsonPath.read(json, "$.prefix");
        LOG.info("Indexing objects with prefix '{}'.", prefix);
        repository.get(prefix)
                .map(entry -> indexer.toImage(entry.key(), entry.resourceSupplier().get()))
                .subscribe(image -> indexer.index(image));

        return ResponseEntity.ok().build();
    }


    @PostMapping("/reindex")
    public ResponseEntity<Void> reindex() {
        indexer.reindex();
        return ResponseEntity.ok().build();
    }


}
