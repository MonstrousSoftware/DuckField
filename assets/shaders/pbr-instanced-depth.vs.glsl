// trimmed version for testing

// required to have same precision in both shader for light structure
#ifdef GL_ES
#define LOWP lowp
#define MED mediump
#define HIGH highp
precision highp float;
#else
#define MED
#define LOWP
#define HIGH
#endif

attribute vec3 a_position;
uniform mat4 u_projViewWorldTrans;
uniform mat4 u_worldTrans;
uniform mat4 u_projViewTrans;



#if defined(diffuseTextureFlag) && defined(blendedFlag)
#define blendedTextureFlag
attribute vec2 a_texCoord0;
varying vec2 v_texCoords0;
#endif


#ifdef PackedDepthFlag
varying float v_depth;
#endif //PackedDepthFlag

// MS
#if defined(instanced)
attribute vec4 i_offset;       // instanced data (X, scale Y, Z, rotation angle around Y)

// MS
mat2 rotate(float angle) {
    return mat2(
    cos(angle), -sin(angle),
    sin(angle), cos(angle)
    );
}
#endif // instanced


void main() {
    #ifdef blendedTextureFlag
    v_texCoords0 = a_texCoord0;
    #endif // blendedTextureFlag




    vec3 morph_pos = a_position;
    #if defined(instanced)
        morph_pos.xz = rotate(i_offset.w)*morph_pos.xz;                         // rotate around Y axis
    morph_pos.y *= i_offset.y;                                        // scale in Y direction
    //pos.z += i_offset.z;
    morph_pos += vec3(i_offset.x, 0.0, i_offset.z);                  // offset in horizontal plane
    #endif

    vec4 pos = u_worldTrans * vec4(morph_pos, 1.0);

    // MS
//    #if defined(instanced)
////    pos.xz = rotate(i_offset.w)*pos.xz;                         // rotate around Y axis
////    pos.y *= i_offset.y;                                        // scale in Y direction
//    //pos.z += i_offset.z;
//    pos += vec4(i_offset.x, 0.0, i_offset.z, 0);                  // offset in horizontal plane
//    #endif


    //v_position = vec3(pos.xyz) / pos.w;
    pos = u_projViewTrans * pos;

    #ifdef PackedDepthFlag
    v_depth = pos.z/pos.w * 0.5 + 0.5;
    #endif //PackedDepthFlag

    gl_Position = pos;
}
