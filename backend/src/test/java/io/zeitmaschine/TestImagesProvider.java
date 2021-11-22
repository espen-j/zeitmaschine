package io.zeitmaschine;


import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.springframework.core.io.ClassPathResource;

public class TestImagesProvider implements ArgumentsProvider {

    private static final String[] images = {"images/IMG_20161208_024708.jpg", "images/IMG_20180614_214734.jpg", "images/IMG_20181001_185137.jpg"};

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) {
        return Stream.of(images)
                .map(image -> new ClassPathResource(image))
                .map(Arguments::of);
    }
}