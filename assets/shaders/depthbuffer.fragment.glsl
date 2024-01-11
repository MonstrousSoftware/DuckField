
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


varying vec2 v_texCoord0;


void main()
{
	vec4 color = texture2D(u_texture, v_texCoord0);

    float grey = color.g;// + 256.0*color.g + 256.0*256.0*color.b + 256.0*256.0*256.0*color.a;
    float scaledGrey = grey; ///500000.0;

    color.r = scaledGrey;
    color.b = scaledGrey;
    color.g = scaledGrey;

    color.a = 1.0;


    gl_FragColor = color;
}
