package zone.rawbot.zeitmaschine.prozessor.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zone.rawbot.zeitmaschine.prozessor.rest.ImageEndpoint;
import zone.rawbot.zeitmaschine.prozessor.s3.Image;

import java.net.URI;

@Service
public class Indexer {

    private final static Logger LOG = LoggerFactory.getLogger(ImageEndpoint.class.getName());
    private final static String ELASTIC_HOST = "http://localhost:9200/zeitmaschine/image";

    public void index(Image image) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpEntity<Image> request = new HttpEntity<>(image);
            URI location = restTemplate
                    .postForLocation(ELASTIC_HOST, request);
            LOG.info("Image successfully indexed: {}", location.toString());
        } catch (RestClientException e) {
            LOG.error("Failed to index image '{}'.", image.getName(), e);
        }
    }
}
