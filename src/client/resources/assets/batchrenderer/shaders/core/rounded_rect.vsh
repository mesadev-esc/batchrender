#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 LocalPos;
layout(location = 3) in vec2 RectSize;
layout(location = 4) in vec4 Radii;
layout(location = 5) in float StrokeWidth;

layout(location = 0) out vec4 vertColor;
layout(location = 1) out vec2 localPos;
layout(location = 2) flat out vec2 rectSize;
layout(location = 3) flat out vec4 radii;
layout(location = 4) flat out float strokeWidth;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertColor = Color;
    localPos = LocalPos;
    rectSize = RectSize;
    radii = Radii;
    strokeWidth = StrokeWidth;
}
