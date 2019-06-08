package io.zeitmaschine.index;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import io.zeitmaschine.s3.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import io.zeitmaschine.s3.S3Repository;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.util.Map;

@Service
public class Indexer {

    private final static Logger LOG = LoggerFactory.getLogger(Indexer.class.getName());
    private final String index;
    private final String indexUrl;
    private final String indexesUrl;
    private final String resourceUrl;

    private S3Repository repository;
    private RestTemplate restTemplate = new RestTemplate();

    @Autowired
    public Indexer(S3Repository repository, IndexerConfig config) {
        this.repository = repository;
        this.index = config.getIndex();
        this.indexesUrl = String.format("%s/_all", config.getHost());
        this.indexUrl = String.format("%s/%s", config.getHost(), config.getIndex());
        this.resourceUrl = String.format("%s/%s", indexUrl, config.getResource());
    }

    @PostConstruct
    void setup() {
        LOG.info("Checking elasticsearch index: '{}'", indexUrl);
        ResponseEntity<String> existing = restTemplate.getForEntity(indexesUrl, String.class);
        ReadContext document = JsonPath.parse(existing.getBody());

        Map<String, String> zm = document.read("$");

        boolean exists = zm.containsKey(index);
        LOG.info("Index '{}' existing: {}", index, exists);
        if (!exists) {
            createIndex();
        }
    }

    private void createIndex() {

        LOG.info("Creating index '{}'.", indexUrl);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.put(indexUrl, null);
    }

    public void index(Image image) {
        try {
            HttpEntity<Image> request = new HttpEntity<>(image);
            URI uri = restTemplate.postForLocation(resourceUrl, request);

            LOG.info("Image successfully indexed: {}", uri.toString());
        } catch (RestClientException e) {
            LOG.error("Failed to index image '{}'.", image.getName(), e);
        }
    }

    public void reindex() {

        // TODO delete index - fails when no such index
        LOG.info("Recreating index '{}'.", indexUrl);
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.delete(indexUrl);

        createIndex();

        // re-index
        repository.getImages(image -> index(image));
    }
}
