package io.zeitmaschine.image;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/image")
public class ImageEndpoint {

    private final ImageService imageService;

    @Autowired
    public ImageEndpoint(ImageService imageService) {
        this.imageService = imageService;
    }

    @GetMapping(value = "/{dimension}", produces = MediaType.IMAGE_JPEG_VALUE)
    public Mono<Resource> image(@PathVariable String dimension, @RequestParam String name) {

        // https://stackoverflow.com/questions/51837086/request-for-reactive-server-response-with-image-content-type-sample
        // https://stackoverflow.com/questions/49259156/spring-webflux-serve-files-from-controller
        try {
            return imageService.getImageByDimension(name, Dimension.valueOf(dimension.toUpperCase()));
        } catch (Exception e) {
            // TODO this leaks internals.
            return Mono.error(e);
        }
    }
}
