package dev.mesa.batchrenderer.client;

public class BUtils {
    @Deprecated
    public static NineSlice nineslicify(BTexture texture, int borderTop, int borderRight, int borderBottom, int borderLeft) {
        return new NineSlice(texture, borderTop, borderRight, borderBottom, borderLeft);
    }
}
