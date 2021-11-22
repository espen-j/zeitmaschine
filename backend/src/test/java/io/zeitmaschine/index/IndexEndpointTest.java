package io.zeitmaschine.index;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import io.zeitmaschine.s3.S3Entry;
import io.zeitmaschine.s3.S3Repository;
import reactor.core.publisher.Flux;

@SpringBootTest
@AutoConfigureWebTestClient
public class IndexEndpointTest {

    @MockBean
    private S3Repository repository;

    @MockBean
    private Indexer indexer;

    @Autowired
    private WebTestClient webClient;

    @Test
    void name() {

        // GIVEN
        String prefix = "test";
        S3Entry entry = S3Entry.of(prefix + "/object123", mock(Resource.class));
        when(repository.get(prefix)).thenReturn(Flux.just(entry));
        when(indexer.toImage(any(), any())).thenReturn(mock(Image.class));

        // WHEN
        webClient.post()
                .uri("/index/index")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(prefix))
                .exchange()
                .expectStatus().is2xxSuccessful();

        // THEN
        verify(repository, times(1)).get(eq(prefix));
        verify(indexer, times(1)).index(any(Image.class));
    }
}