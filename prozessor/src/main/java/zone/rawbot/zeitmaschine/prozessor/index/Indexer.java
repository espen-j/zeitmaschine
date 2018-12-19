package zone.rawbot.zeitmaschine.prozessor.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zone.rawbot.zeitmaschine.prozessor.rest.ImageEndpoint;
import zone.rawbot.zeitmaschine.prozessor.s3.Image;
import zone.rawbot.zeitmaschine.prozessor.s3.S3Repository;

import java.net.URI;

@Service
public class Indexer {

    private final static Logger LOG = LoggerFactory.getLogger(ImageEndpoint.class.getName());
    private final static String ELASTIC_INDEX = "http://localhost:9200/zeitmaschine";
    private final static String ELASTIC_IMAGE_DOCUMENTS = ELASTIC_INDEX + "/image";

    private S3Repository repository;

    @Autowired
    public Indexer(S3Repository repository) {
        this.repository = repository;
    }

    public void index(Image image) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpEntity<Image> request = new HttpEntity<>(image);
            URI location = restTemplate
                    .postForLocation(ELASTIC_IMAGE_DOCUMENTS, request);
            LOG.info("Image successfully indexed: {}", location.toString());
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
