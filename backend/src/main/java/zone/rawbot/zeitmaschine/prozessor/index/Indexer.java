package zone.rawbot.zeitmaschine.prozessor.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zone.rawbot.zeitmaschine.prozessor.s3.Image;
import zone.rawbot.zeitmaschine.prozessor.s3.S3Repository;

import java.net.URI;

@Service
public class Indexer {

    private final static Logger LOG = LoggerFactory.getLogger(Indexer.class.getName());

    private S3Repository repository;

    private final String location;

    @Autowired
    public Indexer(S3Repository repository, IndexerConfig config) {
        this.repository = repository;
        this.location = config.getHost();
    }

    public void index(Image image) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpEntity<Image> request = new HttpEntity<>(image);
            URI uri = restTemplate
                    .postForLocation(location, request);
            LOG.info("Image successfully indexed: {}", uri.toString());
        } catch (RestClientException e) {
            LOG.error("Failed to index image '{}'.", image.getName(), e);
        }
    }

    public void reindex() {

        // TODO delete index - fails when no such index
        RestTemplate restTemplate = new RestTemplate();
        //restTemplate.delete(ELASTIC_INDEX);

        // re-index
        repository.getImages().forEach( image -> index(image));
    }
}
