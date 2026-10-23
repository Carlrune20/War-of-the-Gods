// The vertex shader every gbuffers program shares. The program's .vsh picks what it needs with defines:
// TEXTURED, LIGHTMAP, DIFFUSE (shade by Minecraft's two fixed lights, as entities are without shaders), SKY,
// TERRAIN (block ids for glowing blocks) and WATER.

varying vec4 color;
varying vec2 texcoord;
varying vec2 lmcoord;
varying float fogDist;

#ifdef SKY
varying vec3 viewPos;
#endif

#if defined TERRAIN || defined WATER
attribute vec4 mc_Entity;
// The block's group from block.properties: 1 lava and fire, 2 ember rock, 3 water; see block.properties.
varying float blockKind;
#endif

void main() {
    vec4 pos = gl_ModelViewMatrix * gl_Vertex;
    gl_Position = gl_ProjectionMatrix * pos;
    fogDist = length(pos.xyz);
    gl_FogFragCoord = fogDist;
    color = gl_Color;
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;

#ifdef SKY
    viewPos = pos.xyz;
#endif

#ifdef DIFFUSE
    if (dot(gl_Normal, gl_Normal) > 0.01) {
        vec3 normal = normalize(gl_NormalMatrix * gl_Normal);
        float light0 = max(0.0, dot(normal, normalize(gl_LightSource[0].position.xyz)));
        float light1 = max(0.0, dot(normal, normalize(gl_LightSource[1].position.xyz)));
        color.rgb *= min(1.0, 0.4 + 0.6 * (light0 + light1));
    }
#endif

#if defined TERRAIN || defined WATER
    blockKind = 0.0;
    if (mc_Entity.x == 10001.0 || mc_Entity.x == 10004.0) {
        blockKind = 1.0;
    } else if (mc_Entity.x == 10002.0) {
        blockKind = 2.0;
    } else if (mc_Entity.x == 8.0 || mc_Entity.x == 9.0) {
        blockKind = 3.0;
    }
#endif
}
