package zone.rawbot.zeitmaschine.prozessor.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import zone.rawbot.zeitmaschine.prozessor.image.Dimension;
import zone.rawbot.zeitmaschine.prozessor.s3.S3Repository;

@RestController
@RequestMapping("/image")
public class ImageEndpoint {

    private final static Logger log = LoggerFactory.getLogger(ImageEndpoint.class.getName());

    private S3Repository repository;

    @Autowired
    public ImageEndpoint(S3Repository repository) {
        this.repository = repository;
    }

    @GetMapping(value = "/{name}", produces = MediaType.IMAGE_JPEG_VALUE)
    public Mono<ByteArrayResource> thumbnail(@PathVariable String name) {

        // https://stackoverflow.com/questions/51837086/request-for-reactive-server-response-with-image-content-type-sample
        // https://stackoverflow.com/questions/49259156/spring-webflux-serve-files-from-controller

        byte[] data = repository.getImageAsData(name, Dimension.THUMBNAIL);
        return Mono.just(new ByteArrayResource(data));
    }
}
