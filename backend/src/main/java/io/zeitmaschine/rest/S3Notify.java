package io.zeitmaschine.rest;

import com.jayway.jsonpath.JsonPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.zeitmaschine.index.Indexer;
import io.zeitmaschine.s3.S3Repository;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * S3 notify endpoint.
 */
@RestController
@RequestMapping("/s3")
public class S3Notify {

    private final static Logger log = LoggerFactory.getLogger(S3Notify.class.getName());

    private S3Repository repository;
    private Indexer indexer;

    @Autowired
    public S3Notify(S3Repository repository, Indexer indexer) {
        this.repository = repository;
        this.indexer = indexer;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> notify(@RequestBody String json) {

        List<String> keys = JsonPath.read(json, "$.Records[*].s3.object.key");

        Flux.fromIterable(keys)
                .flatMap(key -> repository.fetchImage(S3Repository.BUCKET_NAME, key)
                            .map(resource -> repository.getImage(key, resource)))
                .subscribe(image -> indexer.index(image));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reindex")
    public ResponseEntity<Void> reindex() {
        indexer.reindex();
        return ResponseEntity.ok().build();
    }


}
