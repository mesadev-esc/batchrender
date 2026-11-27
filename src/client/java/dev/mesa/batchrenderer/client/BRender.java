package dev.mesa.batchrenderer.client;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.state.WindowRenderState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;
import org.joml.*;
import org.lwjgl.system.MemoryUtil;

import java.lang.Math;
import java.nio.ByteBuffer;
import java.util.*;

public class BRender {
    public static class RoundRectCmd {
        public final int x, y, w, h, color, r1, r2, r3, r4, strokeWidth, layer;

        public RoundRectCmd(int x, int y, int w, int h, int color, int r1, int r2, int r3, int r4, int strokeWidth, int layer) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.color = color; this.r1 = r1; this.r2 = r2;
            this.r3 = r3; this.r4 = r4; this.strokeWidth = strokeWidth;
            this.layer = layer;
        }

        public int x() { return x; }
        public int y() { return y; }
        public int w() { return w; }
        public int h() { return h; }
        public int color() { return color; }
        public int r1() { return r1; }
        public int r2() { return r2; }
        public int r3() { return r3; }
        public int r4() { return r4; }
        public int strokeWidth() { return strokeWidth; }
        public int layer() { return layer; }
    }

    private record RectCmd(int x, int y, int w, int h, int color, int layer) {}

    private record TextCmd(BFont font, String text, float x, float y, float size, int color, boolean shadow,
                           boolean centered, int layer, int[] charColors, float[][] bakedQuads,
                           List<BFont.EmojiQuad> emojiQuads) {
        TextCmd(BFont font, String text, float x, float y, float size, int color, boolean shadow, boolean centered, int layer) {
            this(font, text, x, y, size, color, shadow, centered, layer, null, null, null);
        }
    }

    private record PendingDraw(int layer, int type, Object cmd) {}
    private final List<PendingDraw> commands = new ArrayList<>();

    private void queue(RoundRectCmd c) {
        commands.add(new PendingDraw(c.layer(), 0, c));
    }

    private void queue(RectCmd c) {
        queue(new RoundRectCmd(c.x(), c.y(), c.w(), c.h(), c.color(), 0, 0, 0, 0, 0, c.layer()));
    }

    private void queue(TextCmd c) {
        commands.add(new PendingDraw(c.layer(), 2, c));
    }

    private void queue(TextureCmd c) {
        commands.add(new PendingDraw(c.layer(), 3, c));
    }

    private void queue(NineSliceCmd c) {
        commands.add(new PendingDraw(c.layer(), 4, c));
    }

    public static class GradientRoundRectCmd extends RoundRectCmd {
        public final int colorTL, colorTR, colorBL, colorBR;

        public GradientRoundRectCmd(int x, int y, int w, int h, int r1, int r2, int r3, int r4,
                                    int strokeWidth, int layer,
                                    Gradient gradient, GradientDirection dir) {
            super(x, y, w, h, 0, r1, r2, r3, r4, strokeWidth, layer);
            int[] c = BRender.cornersFromGradient(gradient, dir);
            this.colorTL = c[0]; this.colorTR = c[1];
            this.colorBL = c[2]; this.colorBR = c[3];
        }

        public int colorTL() { return colorTL; }
        public int colorTR() { return colorTR; }
        public int colorBL() { return colorBL; }
        public int colorBR() { return colorBR; }
    }

    public record DrawEntry(int layer, int type, Object cmd, Matrix4f pose) {}
    public static final List<DrawEntry> DRAW_LIST = new ArrayList<>();

    private record TextureCmd(
            BTexture texture,
            float x, float y, float w, float h,
            float u0, float v0, float u1, float v1,
            int color,
            boolean tile,
            boolean linear,
            int layer
    ) {}

    private record NineSliceCmd(
            NineSlice slice,
            float x, float y, float w, float h,
            int color,
            boolean linear,
            int layer
    ) {}

    public static class FlushContext {
        public float offsetX = 0;
        public float offsetY = 0;
        public float scaleX = 1.0f;
        public float scaleY = 1.0f;
    }

    @FunctionalInterface
    public interface FlushOption {
        void apply(FlushContext ctx);
    }

    public static FlushOption WithOffset(float x, float y) {
        return ctx -> {
            ctx.offsetX += x;
            ctx.offsetY += y;
        };
    }

    public static FlushOption WithScale(float s) {
        return ctx -> {
            ctx.scaleX *= s;
            ctx.scaleY *= s;
        };
    }

    public void roundRect(int x, int y, int w, int h, int color, int radius, int layer) {
        queue(new RoundRectCmd(x, y, w, h, color, radius, radius, radius, radius, 0, layer));
    }

    public void roundRect(int x, int y, int w, int h, int color, int r1, int r2, int r3, int r4, int layer) {
        queue(new RoundRectCmd(x, y, w, h, color, r1, r2, r3, r4, 0, layer));
    }

    @Deprecated
    public void roundRect(int x, int y, int w, int h, int color, int r1, int r2, int layer) {
        queue(new RoundRectCmd(x, y, w, h, color, r1, r2, r1, r2, 0, layer));
    }

    public void circle(int x, int y, int radius, int color, int layer) {
        queue(new RoundRectCmd(x, y, radius * 2, radius * 2, color, radius, radius, radius, radius, 0, layer));
    }

    public void rect(int x, int y, int w, int h, int color, int layer) {
        queue(new RectCmd(x, y, w, h, color, layer));
    }

    public void stroke(int x, int y, int w, int h, int color, int strokeWidth, int layer) {
        queue(new RoundRectCmd(x, y, w, h, color, 0, 0, 0, 0, strokeWidth, layer));
    }

    public void strokeRounded(int x, int y, int w, int h, int color, int radius, int strokeWidth, int layer) {
        queue(new RoundRectCmd(x, y, w, h, color, radius, radius, radius, radius, strokeWidth, layer));
    }

    @Deprecated
    public void strokeRounded(int x, int y, int w, int h, int color, int r1, int r2, int strokeWidth, int layer) {
        queue(new RoundRectCmd(x, y, w, h, color, r1, r2, r1, r2, strokeWidth, layer));
    }

    public void strokeRounded(int x, int y, int w, int h, int color, int r1, int r2, int r3, int r4, int strokeWidth, int layer) {
        queue(new RoundRectCmd(x, y, w, h, color, r1, r2, r3, r4, strokeWidth, layer));
    }

    public void roundRectStroked(int x, int y, int w, int h, int fillColor, int strokeColor, int radius, int strokeWidth, int layer) {
        queue(new RoundRectCmd(x, y, w, h, fillColor, radius, radius, radius, radius, 0, layer));
        queue(new RoundRectCmd(x, y, w, h, strokeColor, radius, radius, radius, radius, strokeWidth, layer));
    }

    @Deprecated
    public void roundRectStroked(int x, int y, int w, int h, int fillColor, int strokeColor, int r1, int r2, int strokeWidth, int layer) {
        queue(new RoundRectCmd(x, y, w, h, fillColor, r1, r2, r1, r2, 0, layer));
        queue(new RoundRectCmd(x, y, w, h, strokeColor, r1, r2, r1, r2, strokeWidth, layer));
    }

    public void roundRectStroked(int x, int y, int w, int h, int fillColor, int strokeColor, int r1, int r2, int r3, int r4, int strokeWidth, int layer) {
        queue(new RoundRectCmd(x, y, w, h, fillColor, r1, r2, r3, r4, 0, layer));
        queue(new RoundRectCmd(x, y, w, h, strokeColor, r1, r2, r3, r4, strokeWidth, layer));
    }

    public void drawText(BFont font, String text, float x, float y, float size, int color, int layer) {
        if (text == null || text.isEmpty()) return;

        BFont.TextRenderBatch batch = font.getTextRenderBatch(text, x, y, size, color);
        if (batch.fontQuads().isEmpty() && batch.emojiQuads().isEmpty()) return;

        queue(new TextCmd(font, "", x, y, size, color, false, false, layer,
                batch.fontColors().stream().mapToInt(Integer::intValue).toArray(),
                batch.fontQuads().toArray(new float[0][]),
                batch.emojiQuads()));
    }
    public void drawText(BFont font, FormattedCharSequence text, float x, float y, float size, int defaultColor, int layer) {
        if (text == null) return;

        BFont.TextRenderBatch batch = font.getTextRenderBatch(text, x, y, size, defaultColor);
        if (batch.fontQuads().isEmpty() && batch.emojiQuads().isEmpty()) return;

        queue(new TextCmd(font, "", x, y, size, defaultColor, false, false, layer,
                batch.fontColors().stream().mapToInt(Integer::intValue).toArray(),
                batch.fontQuads().toArray(new float[0][]),
                batch.emojiQuads()));
    }

    public void drawTexture(BTexture texture, float x, float y, float w, float h, int color, boolean linear, int layer) {
        queue(new TextureCmd(texture, x, y, w, h, 0, 0, 1, 1, color, false, linear, layer));
    }

    public void drawTextureCropped(BTexture texture, float x, float y, float w, float h,
                                   int srcX, int srcY, int srcW, int srcH, int color, boolean linear, int layer) {
        float u0 = (float) srcX / texture.width;
        float v0 = (float) srcY / texture.height;
        float u1 = (float) (srcX + srcW) / texture.width;
        float v1 = (float) (srcY + srcH) / texture.height;
        queue(new TextureCmd(texture, x, y, w, h, u0, v0, u1, v1, color, false, linear, layer));
    }

    public void drawTextureTiled(BTexture texture, float x, float y, float w, float h, int color, boolean linear, int layer) {
        float u1 = w / texture.width;
        float v1 = h / texture.height;
        queue(new TextureCmd(texture, x, y, w, h, 0, 0, u1, v1, color, true, linear, layer));
    }

    public void drawTexture9Slice(NineSlice slice, float x, float y, float w, float h, int color, boolean linear, int layer) {
        queue(new NineSliceCmd(slice, x, y, w, h, color, linear, layer));
    }

    public void roundRect(int x, int y, int w, int h, Gradient g, GradientDirection dir, int radius, int layer) {
        queue(new GradientRoundRectCmd(x, y, w, h, radius, radius, radius, radius, 0, layer, g, dir));
    }
    public void roundRect(int x, int y, int w, int h, Gradient g, GradientDirection dir, int r1, int r2, int r3, int r4, int layer) {
        queue(new GradientRoundRectCmd(x, y, w, h, r1, r2, r3, r4, 0, layer, g, dir));
    }
    public void roundRect(int x, int y, int w, int h, Gradient g, GradientDirection dir, int r1, int r2, int layer) {
        queue(new GradientRoundRectCmd(x, y, w, h, r1, r2, r1, r2, 0, layer, g, dir));
    }

    public void rect(int x, int y, int w, int h, Gradient g, GradientDirection dir, int layer) {
        queue(new GradientRoundRectCmd(x, y, w, h, 0, 0, 0, 0, 0, layer, g, dir));
    }

    public void circle(int x, int y, int radius, Gradient g, GradientDirection dir, int layer) {
        queue(new GradientRoundRectCmd(x, y, radius*2, radius*2, radius, radius, radius, radius, 0, layer, g, dir));
    }

    public void strokeRounded(int x, int y, int w, int h, Gradient g, GradientDirection dir, int radius, int strokeWidth, int layer) {
        queue(new GradientRoundRectCmd(x, y, w, h, radius, radius, radius, radius, strokeWidth, layer, g, dir));
    }
    public void strokeRounded(int x, int y, int w, int h, Gradient g, GradientDirection dir, int r1, int r2, int strokeWidth, int layer) {
        queue(new GradientRoundRectCmd(x, y, w, h, r1, r2, r1, r2, strokeWidth, layer, g, dir));
    }
    public void strokeRounded(int x, int y, int w, int h, Gradient g, GradientDirection dir, int r1, int r2, int r3, int r4, int strokeWidth, int layer) {
        queue(new GradientRoundRectCmd(x, y, w, h, r1, r2, r3, r4, strokeWidth, layer, g, dir));
    }

    public void roundRectStroked(int x, int y, int w, int h, Gradient fillGradient, GradientDirection dir, int strokeColor, int radius, int strokeWidth, int layer) {
        queue(new GradientRoundRectCmd(x, y, w, h, radius, radius, radius, radius, 0, layer, fillGradient, dir));
        queue(new RoundRectCmd(x, y, w, h, strokeColor, radius, radius, radius, radius, strokeWidth, layer));
    }

    public void stroke(int x, int y, int w, int h, Gradient g, GradientDirection dir, int strokeWidth, int layer) {
        queue(new GradientRoundRectCmd(x, y, w, h, 0, 0, 0, 0, strokeWidth, layer, g, dir));
    }

    public void flush(GuiGraphicsExtractor graphics, FlushOption... options) {

        FlushContext ctx = new FlushContext();
        for (FlushOption option : options) {
            option.apply(ctx);
        }

        Matrix3x2f pose = new Matrix3x2f(graphics.pose());

        if (ctx.offsetX != 0 || ctx.offsetY != 0) {
            pose.translate(ctx.offsetX, ctx.offsetY);
        }
        if (ctx.scaleX != 1.0f || ctx.scaleY != 1.0f) {
            pose.scale(ctx.scaleX, ctx.scaleY);
        }

        Matrix4f currentPose = new Matrix4f()
                .m00(pose.m00())
                .m01(pose.m01())
                .m10(pose.m10())
                .m11(pose.m11())
                .m30(pose.m20())
                .m31(pose.m21());

        for (PendingDraw c : commands) DRAW_LIST.add(new DrawEntry(c.layer(), c.type(), c.cmd(), currentPose));
        commands.clear();

    }

    private static final int MAX_QUADS = 4096;
    private static ProjectionMatrixBuffer projectionBuffer;

    public static void clearPending() {
        DRAW_LIST.clear();
    }

    public static void close() {
        clearPending();
        if (projectionBuffer != null) {
            projectionBuffer.close();
            projectionBuffer = null;
        }
    }

    public static void flushAll() {
        if (DRAW_LIST.isEmpty()) return;
        if (!Batchrenderer.isReady()) {
            clearPending();
            return;
        }
        RenderSystem.assertOnRenderThread();
        WindowRenderState window = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState;
        if (window.width <= 0 || window.height <= 0 || window.guiScale <= 0) {
            clearPending();
            return;
        }
        Projection projection = new Projection();
        projection.setupOrtho(1000, 11000, (float) window.width / window.guiScale,
                (float) window.height / window.guiScale, true);
        if (projectionBuffer == null) projectionBuffer = new ProjectionMatrixBuffer("Batchrenderer projection");
        GpuBufferSlice projectionSlice = projectionBuffer.getBuffer(projection);
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f().translation(0, 0, -11000));
        DRAW_LIST.sort(Comparator.comparingInt(DrawEntry::layer));
        try (GeometryBatch batch = new GeometryBatch(projectionSlice, transform)) {
            for (DrawEntry entry : DRAW_LIST) {
                switch (entry.type()) {
                    case 0 -> batch.rectangle((RoundRectCmd) entry.cmd(), entry.pose());
                    case 2 -> batch.text((TextCmd) entry.cmd(), entry.pose());
                    case 3 -> {
                        TextureCmd c = (TextureCmd) entry.cmd();
                        batch.texture(Batchrenderer.TEXTURE_PIPELINE, c.texture().view, sampler(c.linear(), c.tile()),
                                entry.pose(), c.x(), c.y(), c.w(), c.h(), c.u0(), c.v0(), c.u1(), c.v1(), c.color());
                    }
                    case 4 -> batch.nineSlice((NineSliceCmd) entry.cmd(), entry.pose());
                    default -> throw new IllegalStateException("Unknown draw type: " + entry.type());
                }
            }
            batch.flush();
        } finally {
            clearPending();
        }
    }

    private static GpuSampler sampler(boolean linear, boolean repeat) {
        FilterMode filter = linear ? FilterMode.LINEAR : FilterMode.NEAREST;
        return repeat ? RenderSystem.getSamplerCache().getRepeat(filter) : RenderSystem.getSamplerCache().getClampToEdge(filter);
    }

    private static final class GeometryBatch implements AutoCloseable {
        private final ByteBuffer vertices = MemoryUtil.memAlloc(MAX_QUADS * 4 * Batchrenderer.RECT_FORMAT.getVertexSize());
        private final CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        private final GpuBufferSlice projection;
        private final GpuBufferSlice transform;
        private final RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        private RenderPipeline pipeline;
        private GpuTextureView view;
        private GpuSampler sampler;
        private int quads;

        private GeometryBatch(GpuBufferSlice projection, GpuBufferSlice transform) {
            this.projection = projection;
            this.transform = transform;
        }

        private void prepare(RenderPipeline nextPipeline, GpuTextureView nextView, GpuSampler nextSampler) {
            if (pipeline != nextPipeline || view != nextView || sampler != nextSampler || quads == MAX_QUADS) flush();
            pipeline = nextPipeline;
            view = nextView;
            sampler = nextSampler;
        }

        private void rectangle(RoundRectCmd c, Matrix4f pose) {
            if (c.w() <= 0 || c.h() <= 0) return;
            prepare(Batchrenderer.ROUNDED_RECT_PIPELINE, null, null);
            int tl = c.color(), tr = tl, bl = tl, br = tl;
            if (c instanceof GradientRoundRectCmd g) {
                tl = g.colorTL(); tr = g.colorTR(); bl = g.colorBL(); br = g.colorBR();
            }
            rectVertex(c, pose, 0, 0, tl);
            rectVertex(c, pose, 0, c.h(), bl);
            rectVertex(c, pose, c.w(), c.h(), br);
            rectVertex(c, pose, c.w(), 0, tr);
            quads++;
        }

        private void rectVertex(RoundRectCmd c, Matrix4f pose, float localX, float localY, int color) {
            position(pose, c.x() + localX, c.y() + localY);
            color(color);
            vertices.putFloat(localX).putFloat(localY).putFloat(c.w()).putFloat(c.h());
            float maxRadius = Math.min(c.w(), c.h()) * 0.5f;
            vertices.putFloat(Math.clamp(c.r1(), 0, maxRadius)).putFloat(Math.clamp(c.r2(), 0, maxRadius))
                    .putFloat(Math.clamp(c.r3(), 0, maxRadius)).putFloat(Math.clamp(c.r4(), 0, maxRadius));
            vertices.putFloat(Math.max(0, c.strokeWidth()));
        }

        private void texture(RenderPipeline pipeline, GpuTextureView view, GpuSampler sampler, Matrix4f pose,
                             float x, float y, float w, float h, float u0, float v0, float u1, float v1, int color) {
            if (view == null || w <= 0 || h <= 0) return;
            prepare(pipeline, view, sampler);
            textureVertex(pose, x, y, u0, v0, color);
            textureVertex(pose, x, y + h, u0, v1, color);
            textureVertex(pose, x + w, y + h, u1, v1, color);
            textureVertex(pose, x + w, y, u1, v0, color);
            quads++;
        }

        private void textureVertex(Matrix4f pose, float x, float y, float u, float v, int color) {
            position(pose, x, y);
            vertices.putFloat(u).putFloat(v);
            color(color);
        }

        private void position(Matrix4f pose, float x, float y) {
            vertices.putFloat(pose.m00() * x + pose.m10() * y + pose.m30());
            vertices.putFloat(pose.m01() * x + pose.m11() * y + pose.m31());
            vertices.putFloat(0);
        }

        private void color(int argb) {
            vertices.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >> 24));
        }

        private void text(TextCmd c, Matrix4f pose) {
            if (c.font() == null) return;
            float[][] glyphs = c.bakedQuads() != null ? c.bakedQuads() : c.font().getQuads(c.text(), c.x(), c.y(), c.size());
            GpuSampler linear = sampler(true, false);
            for (int i = 0; i < glyphs.length; i++) {
                float[] q = glyphs[i];
                int color = c.charColors() != null && i < c.charColors().length ? c.charColors()[i] : c.color();
                texture(Batchrenderer.TEXT_PIPELINE, c.font().atlasView, linear, pose,
                        q[0], q[1], q[2] - q[0], q[3] - q[1], q[4], q[5], q[6], q[7], color);
            }
            if (c.emojiQuads() != null) {
                for (BFont.EmojiQuad q : c.emojiQuads()) {
                    texture(Batchrenderer.TEXTURE_PIPELINE, q.textureView(), linear, pose,
                            q.x0(), q.y0(), q.x1() - q.x0(), q.y1() - q.y0(), 0, 0, 1, 1, 0xFFFFFFFF);
                }
            }
        }

        private void nineSlice(NineSliceCmd c, Matrix4f pose) {
            NineSlice s = c.slice();
            BTexture t = s.texture();
            if (c.w() <= 0 || c.h() <= 0) return;
            float sx = Math.min(1, c.w() / Math.max(1, s.borderLeft() + s.borderRight()));
            float sy = Math.min(1, c.h() / Math.max(1, s.borderTop() + s.borderBottom()));
            float[] xs = {c.x(), c.x() + s.borderLeft() * sx, c.x() + c.w() - s.borderRight() * sx, c.x() + c.w()};
            float[] ys = {c.y(), c.y() + s.borderTop() * sy, c.y() + c.h() - s.borderBottom() * sy, c.y() + c.h()};
            float[] us = {0, (float) s.borderLeft() / t.width, 1 - (float) s.borderRight() / t.width, 1};
            float[] vs = {0, (float) s.borderTop() / t.height, 1 - (float) s.borderBottom() / t.height, 1};
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 3; x++) {
                    texture(Batchrenderer.TEXTURE_PIPELINE, t.view, sampler(c.linear(), false), pose,
                            xs[x], ys[y], xs[x + 1] - xs[x], ys[y + 1] - ys[y], us[x], vs[y], us[x + 1], vs[y + 1], c.color());
                }
            }
        }

        private void flush() {
            if (quads == 0) return;
            vertices.flip();
            GpuBufferSlice vertexBuffer = encoder.transientMemory().uploadGpu(vertices, 4, GpuBuffer.USAGE_VERTEX);
            RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            int indexCount = quads * 6;
            indices.requestIndexCount(indexCount);
            indices.resizeToRequestedIndexCount();
            GpuBuffer indexBuffer = indices.getBuffer(indexCount);
            try (RenderPass pass = encoder.createRenderPass(() -> "Batchrenderer batch", target.getColorTextureView(), Optional.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
                pass.setUniform("Projection", projection);
                pass.setUniform("DynamicTransforms", transform);
                if (view != null) pass.setUniform("Sampler0", view, sampler);
                pass.setVertexBuffer(0, vertexBuffer);
                pass.setIndexBuffer(indexBuffer, indices.type());
                pass.drawIndexed(indexCount, 1, 0, 0, 0);
            }
            vertices.clear();
            quads = 0;
        }

        @Override
        public void close() {
            MemoryUtil.memFree(vertices);
        }
    }

    public static int[] cornersFromGradient(Gradient g, GradientDirection dir) {
        return switch (dir) {
            case LEFT_RIGHT ->  new int[]{ g.sample(0), g.sample(1), g.sample(0), g.sample(1) };
            case TOP_BOTTOM ->  new int[]{ g.sample(0), g.sample(0), g.sample(1), g.sample(1) };
            case TOP_LEFT_BOTTOM_RIGHT -> new int[]{ g.sample(0), g.sample(0.5f), g.sample(0.5f), g.sample(1) };
            case TOP_RIGHT_BOTTOM_LEFT -> new int[]{ g.sample(0.5f), g.sample(0), g.sample(1), g.sample(0.5f) };
        };
    }
}