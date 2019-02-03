package zone.rawbot.zeitmaschine.prozessor.image;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class DimensionTest {

    @Test
    void name() {
        String dim= "thumbnail".toUpperCase();

        assertThat(Dimension.valueOf(dim), is(Dimension.THUMBNAIL));
    }
}
