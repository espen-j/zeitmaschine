package io.zeitmaschine.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class MetaDataProcessingRepositoryTest {

    @Mock
    private S3Repository s3Repository;

    @Mock
    private Processor processor;

    @InjectMocks
    private MetaDataProcessingRepository processingRepository;

    @Test
    void contentTypeFilter() {

        //GIVEN
        Flux<S3Entry> objects = Flux.just(S3Entry.builder()
                        .key("image1.jpg")
                        .contentType(MediaType.IMAGE_JPEG_VALUE)
                        .build(),
                S3Entry.builder()
                        .key("blob")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                        .build(),
                S3Entry.builder()
                        .key("image2.jpg")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                        .build(),
                S3Entry.builder()
                        .key("image3.jpeg")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                        .build());
        when(s3Repository.get("")).thenReturn(objects);

        // mimic Processor, maybe extract interface and stub impl.
        when(processor.process(any(S3Entry.class))).thenAnswer(i -> S3Entry.Builder.from(i.getArgument(0)).build());

        // WHEN
        Flux<S3Entry> filtered = processingRepository.get("");

        // THEN
        StepVerifier.create(filtered)
                .assertNext(s3Entry -> assertEquals(s3Entry.key(), "image1.jpg"))
                .assertNext(s3Entry -> assertEquals(s3Entry.key(), "image2.jpg"))
                .assertNext(s3Entry -> assertEquals(s3Entry.key(), "image3.jpeg"))
                .verifyComplete();

        verify(processor, times(3)).process(any(S3Entry.class));
    }
}