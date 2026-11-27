package dev.mesa.batchrenderer.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import dev.mesa.batchrenderer.client.mixin.RenderPipelinesAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

public class Batchrenderer implements ClientModInitializer {
    public static final String MOD_ID = "batchrenderer";
    public static final VertexFormat RECT_FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("LocalPos", GpuFormat.RG32_FLOAT)
            .addAttribute("RectSize", GpuFormat.RG32_FLOAT)
            .addAttribute("Radii", GpuFormat.RGBA32_FLOAT)
            .addAttribute("StrokeWidth", GpuFormat.R32_FLOAT)
            .build();
    public static final RenderPipeline ROUNDED_RECT_PIPELINE = pipeline("rounded_rect", "rounded_rect", "rounded_rect", RECT_FORMAT, false);
    public static final RenderPipeline TEXT_PIPELINE = pipeline("text", "text", "text", DefaultVertexFormat.POSITION_TEX_COLOR, true);
    public static final RenderPipeline TEXTURE_PIPELINE = pipeline("texture", "text", "texture", DefaultVertexFormat.POSITION_TEX_COLOR, true);
    private static boolean ready;

    private static RenderPipeline pipeline(String name, String vertex, String fragment, VertexFormat format, boolean textured) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath(MOD_ID, "pipeline/" + name))
                .withVertexShader(Identifier.fromNamespaceAndPath(MOD_ID, "core/" + vertex))
                .withFragmentShader(Identifier.fromNamespaceAndPath(MOD_ID, "core/" + fragment))
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withCull(false)
                .withVertexBinding(0, format)
                .withPrimitiveTopology(PrimitiveTopology.QUADS);
        if (textured) builder.withBindGroupLayout(BindGroupLayouts.SAMPLER0);
        return RenderPipelinesAccessor.batchrenderer$register(builder.build());
    }

    @Override
    public void onInitializeClient() {
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                Identifier.fromNamespaceAndPath(MOD_ID, "assets"), (ResourceManagerReloadListener) manager -> {
                    ready = false;
                    BRender.clearPending();
                    if (Test.font != null) Test.font.close();
                    if (Test.texture != null) Test.texture.close();
                    Test.font = new BFont(Identifier.fromNamespaceAndPath(MOD_ID, "fonts/noto_sans_regular.ttf"), new BFontEmojiResolver());
                    Test.texture = new BTexture(Identifier.fromNamespaceAndPath(MOD_ID, "textures/mc_button.png"));
                    Test.nineSlice = new NineSlice(Test.texture, 4, 4, 4, 4);
                    ready = true;
                });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommands.literal("batchrenderertest").executes(context -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> {
                        if (mc.player != null) mc.player.closeContainer();
                        mc.setScreenAndShow(new Test());
                    });
                    return 1;
                })));
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
            ready = false;
            BRender.close();
            if (Test.font != null) Test.font.close();
            if (Test.texture != null) Test.texture.close();
        });
    }

    public static boolean isReady() {
        return ready;
    }
}
