package io.zeitmaschine.image;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class ImageOperationService {

    // https://github.com/spring-projects/spring-framework/issues/23961
    // mobile shots around 3MB, Fujifilm 5MB, RAW 25.. 20MB is a lot leeway.
    // serverside: https://github.com/spring-projects/spring-framework/blob/master/src/docs/asciidoc/web/webflux.adoc#webflux-config-message-codecs
    private static final int MAX_EXCHANGE_MEMORY_SIZE = 1024 * 1024 * 20;

    private final WebClient webClient;

    @Autowired
    public ImageOperationService(ImageOperationConfig config) {
        this.webClient = WebClient
                .builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_EXCHANGE_MEMORY_SIZE))
                .baseUrl(config.getHost())
                .build();
    }

    public Mono<Resource> resize(Resource image, Dimension dimension) {
        return webClient
                .post()
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
