package zone.rawbot.zeitmaschine.prozessor.image;

import com.mortennobel.imagescaling.DimensionConstrain;
import com.mortennobel.imagescaling.ResampleFilters;
import com.mortennobel.imagescaling.ResampleOp;

import java.awt.image.BufferedImage;

public class Scaler {

    public static BufferedImage scale(BufferedImage input, Dimension dimension) {
        DimensionConstrain maxDimension = DimensionConstrain.createMaxDimension(dimension.getSize(), dimension.getSize());
        ResampleOp resizeOp = new ResampleOp(maxDimension);

        resizeOp.setFilter(ResampleFilters.getLanczos3Filter());
        return resizeOp.filter(input, null);
    }
}
