package io.zeitmaschine.image;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public class ImagingService {

    private final WebClient webClient;

    public ImagingService() {
        this.webClient = WebClient
                .builder()
                .baseUrl("http://localhost:9100")
                .build();
    }

    public Mono<Resource> resize(Resource image, Dimension dimension) {
        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("resize")
                        .queryParam("width", dimension.getSize())
                        .build())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_JPEG_VALUE)
                .body(BodyInserters.fromResource(image))
                .accept(MediaType.IMAGE_JPEG)
                .retrieve()
                .bodyToMono(byte[].class)
                .map(bytes -> new ByteArrayResource(bytes));
    }
}
