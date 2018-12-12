package zone.rawbot.zeitmaschine.prozessor.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import zone.rawbot.zeitmaschine.prozessor.s3.Image;
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

    @GetMapping("/{name}")
    public Resource thumbnail(@PathVariable String name) {

        // TODO
        // https://stackoverflow.com/questions/51837086/request-for-reactive-server-response-with-image-content-type-sample
        // https://stackoverflow.com/questions/49259156/spring-webflux-serve-files-from-controller

        return repository.getImageAsData(name);
        /*return ResponseEntity
                .ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(BodyInserters.fromResource(image));*/

/*        Flux<DataBuffer> publisher = DataBufferUtils.readInputStream(() -> repository.getImageAsData(name), new DefaultDataBufferFactory(), 1024);
        BodyInserter<Flux<DataBuffer>, ReactiveHttpOutputMessage> inserter = BodyInserters.fromDataBuffers(publisher);
        return ServerResponse
                .ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(inserter);*/

    }

    @GetMapping()
    public Flux<Image> images() {
        return repository.getImages();
    }
}
