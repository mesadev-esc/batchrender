# batchrenderer

A Minecraft 26.3 Fabric UI batch and layer renderer using Minecraft's RenderPearl Vulkan backend. Supports rounded rectangles, strokes, gradients, SDF text, emoji, textures, and nine-slice panels.

Requires Java 25 and Fabric Loader 0.19.3 or newer. Build with `./gradlew build`; launch the development client with `./gradlew runClient` (Vulkan). For other launchers, add the game arguments `--graphicsBackend vulkan`.

Import the API from `mesa`. Enqueue draws on a `BRender`, then call `flush(GuiGraphicsExtractor, FlushOption...)` during screen extraction. Draws render after the vanilla GUI in ascending layer order, preserving submission order within each layer. `WithOffset` and `WithScale` apply per flush. Consecutive compatible draws share a GPU batch, split automatically at 4,096 quads.

Use `/batchrenderertest` in a world to open the demo screen. All mod IDs, asset namespaces, mixin names, and Java packages use `batchrenderer`.

Derived from BSprout's Minecraft UI rendering API, under LGPL-3.0-only.
