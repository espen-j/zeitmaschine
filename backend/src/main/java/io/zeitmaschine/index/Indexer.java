package io.zeitmaschine.index;

import java.net.URI;
import java.util.Map;

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
import org.springframework.web.reactive.function.client.WebClient;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;

import io.zeitmaschine.s3.S3Entry;

@Service
public class Indexer {

    private final static Logger LOG = LoggerFactory.getLogger(Indexer.class.getName());
    private final String index;
    private final String indexUrl;
    private final String indexesUrl;
    private final String resourceUrl;
    private final WebClient webClient;

    private RestTemplate restTemplate = new RestTemplate();

    @Autowired
    public Indexer(IndexerConfig config) {
        this.index = config.getIndex();
        this.indexesUrl = String.format("%s/_all", config.getHost());
        this.indexUrl = String.format("%s/%s", config.getHost(), config.getIndex());
        this.resourceUrl = String.format("%s/%s", indexUrl, config.getResource());

        this.webClient = WebClient
                .builder()
                .baseUrl(config.getHost())
                .build();
        LOG.info("elastic: {}", indexesUrl);
    }

    public void initIndex() {
        LOG.info("Checking elasticsearch index: '{}'", indexUrl);
        ResponseEntity<String> existing = restTemplate.getForEntity(indexesUrl, String.class);
        ReadContext document = JsonPath.parse(existing.getBody());

        Map<String, String> zm = document.read("$");

        boolean exists = zm.containsKey(index);
        LOG.info("Index '{}' existing: {}", index, exists);
        if (!exists) {
            LOG.info("Creating index '{}'.", indexUrl);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            restTemplate.put(indexUrl, null);
        }

    }

    public void index(S3Entry entry) {
        try {
            Image payload = Image.from(entry.key())
                    .createDate(entry.created())
                    .location(entry.location())
                    .build();
            HttpEntity<Image> request = new HttpEntity<>(payload);
            URI uri = restTemplate.postForLocation(resourceUrl, request);

            LOG.info("Image '{}' successfully indexed: {}", entry.key(), uri.toString());
        } catch (RestClientException e) {
            LOG.error("Failed to index image '{}'.", entry.key(), e);
        }
    }

    public void wipe() {
        LOG.info("Deleting index '{}'.", indexUrl);
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.delete(indexUrl);
    }
}
