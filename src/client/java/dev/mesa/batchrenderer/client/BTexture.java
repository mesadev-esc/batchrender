package dev.mesa.batchrenderer.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class BTexture implements ResourceManagerReloadListener, AutoCloseable {
    public static final Logger LOGGER = LoggerFactory.getLogger("batchrenderer");
    public GpuTexture texture;
    public GpuTextureView view;
    public int width;
    public int height;
    private final Identifier location;

    public BTexture(Identifier location) {
        this.location = location;
        load();
    }

    private void load() {
        close();
        ByteBuffer pixels;
        boolean fromStb = true;
        try (InputStream stream = Minecraft.getInstance().getResourceManager().open(location);
             MemoryStack stack = MemoryStack.stackPush()) {
            byte[] bytes = stream.readAllBytes();
            ByteBuffer raw = MemoryUtil.memAlloc(bytes.length);
            try {
                raw.put(bytes).flip();
                IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), channels = stack.mallocInt(1);
                pixels = STBImage.stbi_load_from_memory(raw, w, h, channels, 4);
                if (pixels == null) throw new IllegalStateException(STBImage.stbi_failure_reason());
                width = w.get(0);
                height = h.get(0);
            } finally {
                MemoryUtil.memFree(raw);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load texture {}: {}", location, e.getMessage());
            pixels = makeMissingTexture();
            fromStb = false;
            width = height = 16;
        }
        try {
            texture = RenderSystem.getDevice().createTexture(location.toString(),
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                    GpuFormat.RGBA8_UNORM, width, height, 1, 1);
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, pixels, 0, 0, 0, 0, width, height);
            view = RenderSystem.getDevice().createTextureView(texture);
        } catch (RuntimeException e) {
            close();
            throw e;
        } finally {
            if (fromStb) STBImage.stbi_image_free(pixels);
            else MemoryUtil.memFree(pixels);
        }
    }

    private static ByteBuffer makeMissingTexture() {
        ByteBuffer pixels = MemoryUtil.memAlloc(16 * 16 * 4);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                byte channel = (byte) ((x < 8) != (y < 8) ? 255 : 0);
                pixels.put(channel).put((byte) 0).put(channel).put((byte) 255);
            }
        }
        return pixels.flip();
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        load();
    }

    @Override
    public void close() {
        if (view != null) { view.close(); view = null; }
        if (texture != null) { texture.close(); texture = null; }
    }

    public static Logger getLogger() {
        return LOGGER;
    }
}
