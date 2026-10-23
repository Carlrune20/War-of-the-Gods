// The fragment shader every gbuffers program shares, picked by the same defines as gbuffers_vertex.glsl and also:
// ENTITY (hurt flash colour), SKY_BASIC, SKY_TEXTURED, CLOUDS, EYES (glowing eyes) and NO_FOG.
// It writes the colour to colortex0 and what glows to colortex4, which composite blurs into bloom.

#include "/lib/settings.glsl"
#include "/lib/map.glsl"
#include "/lib/fog.glsl"
#ifdef LIGHTMAP
#include "/lib/light.glsl"
#endif

uniform sampler2D texture;

#ifdef ENTITY
uniform vec4 entityColor;
#endif

#ifdef SKY
uniform vec3 upPosition;
varying vec3 viewPos;
#endif

#if defined TERRAIN || defined WATER
varying float blockKind;
#endif

varying vec4 color;
varying vec2 texcoord;
varying vec2 lmcoord;
varying float fogDist;

void main() {
    int map = currentMap();
    vec4 albedo = color;
#ifdef TEXTURED
    albedo *= texture2D(texture, texcoord);
#endif
#ifdef ENTITY
    albedo.rgb = mix(albedo.rgb, entityColor.rgb, entityColor.a);
#endif

#ifdef WATER
    if (blockKind > 2.5) {
        if (map == MAP_FORINTHRY) {
            // Stagnant: darker and hard to see into.
            albedo.rgb *= 0.8;
            albedo.a = min(1.0, albedo.a * 1.3);
        } else if (map == MAP_MISTHALIN) {
            // The River Lum, clear and blue.
            albedo.rgb *= vec3(0.85, 0.97, 1.1);
            albedo.a *= 0.85;
        }
    }
#endif

    vec3 c = albedo.rgb;
    vec3 glow = vec3(0.0);

#ifdef SKY_BASIC
    if (map == MAP_FORINTHRY && isEyeInWater == 0) {
        float up = dot(normalize(viewPos), normalize(upPosition));
        // Stars are drawn grey, as bright as they are opaque; the sky dome at midnight is black.
        bool star = albedo.r == albedo.g && albedo.g == albedo.b && albedo.r > 0.01;
        if (star) {
            c = vec3(1.0, 0.7, 0.55) * albedo.r * 0.3;
        } else {
            c = mix(FORINTHRY_HORIZON, FORINTHRY_ZENITH, smoothstep(-0.05, 0.5, up));
            albedo.a = 1.0;
        }
    }
#endif

#ifdef SKY_TEXTURED
    if (map == MAP_FORINTHRY) {
        // A blood moon through the smoke.
        c *= vec3(1.0, 0.38, 0.28);
        glow = c * 0.5;
    }
#endif

#ifdef CLOUDS
    if (map == MAP_FORINTHRY) {
        c *= vec3(0.3, 0.2, 0.2);
    } else if (map == MAP_MISTHALIN) {
        c *= vec3(1.0, 0.98, 0.95);
    }
#endif

#ifdef LIGHTMAP
    vec3 light = worldLight(lmcoord, map);
#if defined TERRAIN
    if (blockKind > 0.5 && blockKind < 2.5) {
        // Lava and fire glow all over; ember rock only where its cracks are hot. Fog of war darkens what a team
        // has not seen, and its glow with it, so lava does not give away unexplored ground.
        float bright = max(albedo.r, max(albedo.g, albedo.b));
        float amount = blockKind < 1.5 ? 1.0 : smoothstep(0.45, 0.8, bright) * step(albedo.b * 1.6, albedo.r);
        vec2 level = lightLevels(lmcoord);
        amount *= max(level.x, level.y);
        light = max(light, vec3(amount));
        glow = albedo.rgb * amount;
    }
#endif
    c *= light;
#endif

#ifdef EYES
    // Glowing eyes: full bright whatever the light, the night or the fog of war, and they bloom.
    glow = c;
#endif

#ifndef NO_FOG
    float fog = fogAmount(fogDist);
    c = mix(c, mapFogColor(map), fog);
    glow *= 1.0 - fog;
#endif

    gl_FragData[0] = vec4(c, albedo.a);
    gl_FragData[1] = vec4(glow, albedo.a);
}
