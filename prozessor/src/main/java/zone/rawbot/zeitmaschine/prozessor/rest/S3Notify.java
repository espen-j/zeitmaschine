package zone.rawbot.zeitmaschine.prozessor.rest;

import com.jayway.jsonpath.JsonPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import zone.rawbot.zeitmaschine.prozessor.index.Indexer;
import zone.rawbot.zeitmaschine.prozessor.s3.S3Repository;

import java.util.List;

/**
 * S3 notify endpoint.
 */
@RestController
public class S3Notify {

    private final static Logger log = LoggerFactory.getLogger(S3Notify.class.getName());

    private S3Repository repository;
    private Indexer indexer;

    @Autowired
    public S3Notify(S3Repository repository, Indexer indexer) {
        this.repository = repository;
        this.indexer = indexer;
    }

    @PostMapping("api/webhook")
    public ResponseEntity post(@RequestBody String json) {

        List<String> keys = JsonPath.read(json, "$.Records[*].s3.object.key");

        keys.stream()
                .map(key -> repository.getImage(key))
                .filter(optional -> optional.isPresent())
                .map(optional -> optional.get())
                .forEach(image -> indexer.index(image));
        return ResponseEntity.ok().build();
    }



}
