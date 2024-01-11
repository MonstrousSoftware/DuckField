// shader for model instancing

in vec3 a_position;


in vec4 i_offset;       // instanced data (X, Y-scale, Z, rotation angle around Y)

uniform mat4 u_worldTrans;
uniform mat4 u_projTrans;



mat2 rotate(float angle) {
    return mat2(
    cos(angle), -sin(angle),
    sin(angle), cos(angle)
    );
}

void main () {


    vec4 pos = u_worldTrans * vec4(a_position, 1.0);            // object space to world space
    pos.xz = rotate(i_offset.w)*pos.xz;                         // rotate around Y axis
    pos.y *= i_offset.y;                                        // scale in Y direction
    pos += vec4(i_offset.x, 0, i_offset.z, 0);                  // offset in horizontal plane

    gl_Position =  u_projTrans * pos;
}
