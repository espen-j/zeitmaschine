package io.zeitmaschine.index;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;

import io.zeitmaschine.s3.S3Repository;
import reactor.util.retry.Retry;

@Service
public class Indexer {

    private final static Logger LOG = LoggerFactory.getLogger(Indexer.class.getName());
    private final String index;
    private final String indexUrl;
    private final String indexesUrl;
    private final String resourceUrl;
    private final WebClient webClient;

    private S3Repository repository;
    private RestTemplate restTemplate = new RestTemplate();

    @Autowired
    public Indexer(S3Repository repository, IndexerConfig config) {
        this.repository = repository;
        this.index = config.getIndex();
        this.indexesUrl = String.format("%s/_all", config.getHost());
        this.indexUrl = String.format("%s/%s", config.getHost(), config.getIndex());
        this.resourceUrl = String.format("%s/%s", indexUrl, config.getResource());

        this.webClient = WebClient
                .builder()
                .baseUrl(config.getHost())
                .build();
    }

    @PostConstruct
    void setup() {

        LOG.info("elastic: {}", indexesUrl);

        LOG.info("Waiting for elastic..");

        webClient.get()
                .uri("_cat/health")
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(Retry.backoff(5, Duration.of(2, ChronoUnit.SECONDS))
                        .doAfterRetry(retrySignal -> LOG.info("Retry failed."))
                        .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                            LOG.error("Elastic not ready. Aborting..");
                            throw new RuntimeException(retrySignal.failure().getMessage());
                        }))
                .doOnSuccess(s -> {
                    LOG.info("Checking elasticsearch index: '{}'", indexUrl);
                    ResponseEntity<String> existing = restTemplate.getForEntity(indexesUrl, String.class);
                    ReadContext document = JsonPath.parse(existing.getBody());

                    Map<String, String> zm = document.read("$");

                    boolean exists = zm.containsKey(index);
                    LOG.info("Index '{}' existing: {}", index, exists);
                    if (!exists) {
                        createIndex();
                    }
                })
                .block();
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
        repository.getImages()
                .map(tuple -> toImage(tuple.name(), tuple.resource()))
                .subscribe(image -> index(image));
    }

    Image toImage(String key, Resource resource) {

        // FIXME contenttype not checked!
        try {
            Image.Builder builder = Image.from(key);

            Metadata metadata = ImageMetadataReader.readMetadata(resource.getInputStream());
            Optional<ExifSubIFDDirectory> subIFDDirectory = Optional.ofNullable(metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class));
            subIFDDirectory.ifPresent(subIFD -> builder.createDate(subIFD.getDateOriginal()));

            Optional<GpsDirectory> gpsDirectory = Optional.ofNullable(metadata.getFirstDirectoryOfType(GpsDirectory.class));
            gpsDirectory.ifPresent(gps -> {
                GeoLocation geoLocation = gps.getGeoLocation();
                if (geoLocation != null)
                    builder.location(geoLocation.getLatitude(), geoLocation.getLongitude());
            });
            return builder.build();
        } catch (IOException | ImageProcessingException e) {
            throw new RuntimeException("Error reading metadata from image.", e);
        }
    }

}
