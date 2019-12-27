package io.zeitmaschine.index;

import com.jayway.jsonpath.JsonPath;
import io.zeitmaschine.s3.S3Repository;
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
 *
 * TODO Change endpoint /s3 -> /index: Needs minio (s3) webhook reconfiguration!
 */
@RestController
@RequestMapping("/s3")
public class IndexEndpoint {

    private S3Repository repository;
    private Indexer indexer;

    @Autowired
    public IndexEndpoint(S3Repository repository, Indexer indexer) {
        this.repository = repository;
        this.indexer = indexer;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> notify(@RequestBody String json) {

        List<String> keys = JsonPath.read(json, "$.Records[*].s3.object.key");

        Flux.fromIterable(keys)
                .flatMap(key -> repository.get(S3Repository.BUCKET_NAME, key)
                            .map(resource -> indexer.toImage(key, resource)))
                .subscribe(image -> indexer.index(image));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reindex")
    public ResponseEntity<Void> reindex() {
        indexer.reindex();
        return ResponseEntity.ok().build();
    }


}
