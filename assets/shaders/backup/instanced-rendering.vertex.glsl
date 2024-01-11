// shader for model instancing

in vec4 a_position;
in vec2 a_texCoord0;
in vec3 a_normal;

in vec4 i_offset;       // instanced data (X, Y, Z, rotation angle around Y)

uniform mat4 u_projViewTrans;

out vec2 v_diffuseUV;


mat2 rotate(float angle) {
    return mat2(
    cos(angle), -sin(angle),
    sin(angle), cos(angle)
    );
}

void main () {
    v_diffuseUV = a_texCoord0;

    vec4 pos = a_position;
    pos.xz = rotate(i_offset.w)*pos.xz;                 // rotate around Y axis
    pos += vec4(i_offset.x, i_offset.y, i_offset.z, 0.0);      // offset in horizontal plane
    gl_Position = u_projViewTrans * pos;
}
