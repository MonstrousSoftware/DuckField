// vignette
//


#ifdef GL_ES
#define LOWP lowp
#define MED mediump
#define HIGH highp
precision mediump float;
#else
#define MED
#define LOWP
#define HIGH
#endif


uniform sampler2D u_texture;
uniform vec2 u_resolution;
uniform float u_time;

varying vec4 v_color;
varying vec2 v_texCoord0;






void main()
{
	vec4 color = texture2D(u_texture, v_texCoord0);

    // vignette effect
    vec2 uv = v_texCoord0;
    uv *=  1.0 - uv.yx;   //vec2(1.0)- uv.yx; -> 1.-u.yx; Thanks FabriceNeyret !
    float vig = uv.x*uv.y * 15.0; // multiply with sth for intensity
    vig = pow(vig, 0.25); // change pow for modifying the extend of the  vignette
    color.rgb = mix(color.rgb, color.rgb*vig, 0.9);

    // increase contrast
    color.rgb = (color.rgb - 0.5) * 1.2 + 0.5;

    gl_FragColor = color;
}
