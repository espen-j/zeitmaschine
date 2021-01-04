package io.zeitmaschine.index;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import reactor.test.StepVerifier;

@Testcontainers
class IndexerTest {

    private static final Logger LOG = LoggerFactory.getLogger(IndexerTest.class.getName());

    private static final String ELASTICSEARCH_VERSION = "6.5.4";
    private static final String ELASTIC_CONTAINER = "docker.elastic.co/elasticsearch/elasticsearch";

    private static final Integer ELASTIC_PORT = 9200;

    // will be shared between test methods
    @Container
    private static GenericContainer elasticContainer = new ElasticsearchContainer(
            DockerImageName
                    .parse(ELASTIC_CONTAINER)
                    .withTag(ELASTICSEARCH_VERSION))
            .withExposedPorts(ELASTIC_PORT);

    private String elasticHost;

    @BeforeEach
    private void beforeAll() {
        this.elasticHost = "http://" + elasticContainer.getHost() + ":" + elasticContainer.getMappedPort(ELASTIC_PORT);
    }

    @Test
    void health() {
        IndexerConfig config = new IndexerConfig();
        config.setHost(elasticHost);
        StepVerifier.create(new IndexerHealthIndicator(config).health())
                .expectNext(Health.up().build())
                .expectComplete()
                .verify();
    }

    /* Naked elastic requests for creation and deletion of an index. */
    @Test
    void indexCreateDelete() {

        WebTestClient index = WebTestClient.bindToServer()
                .baseUrl(elasticHost)
                .filter(logRequest())
                .build();

        index
                .put()
                .uri("new-index")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(response -> assertThat(response.getResponseBody(), containsString("\"index\":\"new-index\"")));

        index
                .delete()
                .uri("new-index")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(response -> assertThat(response.getResponseBody(), is("{\"acknowledged\":true}")));

        index
                .get()
                .uri("_all")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_JPEG_VALUE)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(response -> assertThat(response.getResponseBody(), is("{}")));
    }

    private ExchangeFilterFunction logRequest() {
        return (clientRequest, next) -> {
            LOG.info("Request: {} {}", clientRequest.method(), clientRequest.url());
            clientRequest.headers()
                    .forEach((name, values) -> values.forEach(value -> LOG.info("{}={}", name, value)));
            return next.exchange(clientRequest);
        };
    }
}