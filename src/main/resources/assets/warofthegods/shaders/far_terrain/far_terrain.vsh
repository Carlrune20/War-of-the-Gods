#version 120

// The RTS view's far terrain (LodTerrain) under the War of the Gods shader pack. Its light comes from a texture with
// a texel per fog of war cell, laid over it by OpenGL's texture coordinate generation from positions, done here.

varying vec4 color;
varying vec2 lightCoord;

void main() {
    gl_Position = ftransform();
    color = gl_Color;
    vec4 generated = vec4(dot(gl_Vertex, gl_ObjectPlaneS[0]), dot(gl_Vertex, gl_ObjectPlaneT[0]), 0.0, 1.0);
    lightCoord = (gl_TextureMatrix[0] * generated).xy;
}
