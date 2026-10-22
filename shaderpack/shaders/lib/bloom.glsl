#ifndef BLOOM_GLSL
#define BLOOM_GLSL

uniform float viewWidth;
uniform float viewHeight;

// One direction of a wide Gaussian blur over what glows, with taps spread wider on taller screens.
vec3 blurGlow(sampler2D glow, vec2 coord, vec2 direction) {
    float spread = max(1.0, viewHeight / 540.0) * 2.0;
    vec2 step = direction * spread / vec2(viewWidth, viewHeight);
    vec3 sum = texture2D(glow, coord).rgb * 0.1585;
    sum += (texture2D(glow, coord + step * 1.0).rgb + texture2D(glow, coord - step * 1.0).rgb) * 0.1465;
    sum += (texture2D(glow, coord + step * 2.0).rgb + texture2D(glow, coord - step * 2.0).rgb) * 0.1156;
    sum += (texture2D(glow, coord + step * 3.0).rgb + texture2D(glow, coord - step * 3.0).rgb) * 0.0778;
    sum += (texture2D(glow, coord + step * 4.0).rgb + texture2D(glow, coord - step * 4.0).rgb) * 0.0447;
    sum += (texture2D(glow, coord + step * 5.0).rgb + texture2D(glow, coord - step * 5.0).rgb) * 0.0219;
    sum += (texture2D(glow, coord + step * 6.0).rgb + texture2D(glow, coord - step * 6.0).rgb) * 0.0092;
    return sum;
}

#endif
