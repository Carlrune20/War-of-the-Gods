# Shaders

War of the Gods has an OptiFine shader pack of its own, with a look for each map. It works in first person and in
the RTS view, far terrain included, and makes lava, ember rock, fire and the eyes of demons, ghosts, hellhounds,
gargoyles, dragons and centaurs glow.

| Map | Look |
| --- | --- |
| **Misthalin** | Kingdom green and gold, with warm sunlight, cool blue moonlight and soft gold torchlight. The River Lum is a clearer blue. |
| **Forinthry** | Still always midnight, but the ash overhead glows red with the lava below, so open ground is lit a dim ember red under a smoky crimson horizon and a blood moon. Colour is drained from everything but fire; lava and the hot cracks of ember rock bloom. Its ponds are murkier. |
| Lobby and anywhere else | Minecraft's own colours with the pack's bloom and vignette. |

## Using it

1. Install [OptiFine](https://optifine.net/downloads) for 1.7.10 (HD U E7) next to the mod.
2. Start the game once. The mod puts the pack in `shaderpacks/War of the Gods` and keeps it up to date with the mod.
3. In **Options > Video Settings > Shaders**, pick **War of the Gods**.

Under shaders OptiFine builds a set number of chunks each frame (**Video Settings > Performance > Chunk Updates**,
1 unless changed), and shaders lower the frame rate, so new ground used to fill in slowly while the CPU and GPU sat
mostly idle. The mod now spends up to `chunkUpdateMillis` (4 by default) of each frame building chunks still waiting
to be drawn. For faster still, raise **Chunk Updates** to 3 to 5 and turn on **Dynamic Updates**.

**Shader Options** in the same screen has sliders for bloom, Forinthry's ember glow and the vignette, and a switch for
each map's colour grading. To keep a pack of your own edits, copy the folder under another name first: the mod
replaces `War of the Gods` whenever its version changes. To stop the mod installing it, set `installShaderPack=false`
in the client's config. The pack also comes on its own, as `WarOfTheGods.zip` in the build's `shader-pack` artifact.

## How it knows the map

A shader can't ask the mod anything, so the server tells it through the time. The game dimension's day count is
`65536 + 16 × map + day in the moon's cycle` (`WorldProviderRts.gameTime`), and `shaderpack/shaders/lib/map.glsl`
reads the map back from OptiFine's `worldDay` uniform. The time of day and the moon's phase are unchanged, and the
overworld never counts that many days, so the lobby gets no map's look. A new map needs its look added to `map.glsl`
(`MAP_...`), `light.glsl`, `fog.glsl`, `gbuffers_fragment.glsl` and `final.fsh`; its number is its place in `GameMap`.

## How it is built

- `gbuffers_*` programs share `lib/gbuffers_vertex.glsl` and `lib/gbuffers_fragment.glsl`, picking what they need
  with defines. Light always starts from Minecraft's lightmap, so night vision, brightness and the RTS view's fog of
  war work as without shaders. Fog comes from Minecraft's own fog settings, which the RTS view pushes out of sight.
- What glows goes to `colortex4`: blocks listed in `block.properties` (lava, fire, glowstone and the mod's ember rock
  and fire) and glowing eyes, which the mod draws through `gbuffers_spidereyes`. Fog of war dims lava's glow with its
  light, so unexplored lava gives nothing away.
- `composite` and `composite1` blur that into bloom, and `final` grades each map's colours.
- The RTS view's outlines, rings and markers are drawn with OpenGL's fixed pipeline, which shader programs don't
  follow, so the mod sets the program aside while it draws them into the colour buffer (`ShaderPack.beginPlain`).
  They then go through the same bloom and grading as the rest of the world.
- The RTS view's far terrain gets a program of its own under this pack (`ShaderPack.beginFarTerrain`, from
  `src/main/resources/assets/warofthegods/shaders/far_terrain`). It lights the terrain the same way the pack lights
  blocks, Forinthry's ember glow included, and makes lava, fire and ember rock glow into the bloom. Each cell carries
  how much of it glows in its colour's alpha, and the fog of war texture carries the light level in its alpha. Keep
  this program in step with `light.glsl`. Under another pack, the far terrain is drawn plain.

CI compiles every program with `glslangValidator` (`.github/scripts/shader-check.py`), resolving `#include` as
OptiFine does. Run the same check locally with `python3 .github/scripts/shader-check.py shaderpack/shaders`.
