# DuckField

A [libGDX](https://libgdx.com/) project generated with [gdx-liftoff](https://github.com/tommyettinger/gdx-liftoff).

![](screenshot.png)

version 1.1 18/03/2024 (with thanks to Antz)

A demonstrator of instancing of a gltf model in combination with the PBR shader of gdx-gltf.
Requires OpenGL ES 3.0.



Note: GLTF models need to be exported from Blender using GLTF Separate, not GLTF Embedded.

Update: the derived PBR shader provider allows to combine instanced rendering of some assets with normal rendering of other assets, applying the relevant shader as needed.
The PBR depth shader is also subclassed to support shadows.


How it works:
When I create the SceneManager, I pass a subclassed shader provider and a subclassed depth shader provider.
```
sceneManager = new SceneManager( new MyPBRShaderProvider(), new MyPBRDepthShaderProvider() );
```
These are just there to substitute a subclassed shader instead of PBRShader or PBDDepthShader, with two differences:
- if the renderable's mesh is an instanced mesh, then add a line to the shader prefix to define 'instanced'
- substitute a vertex shader that has been adapted from the original PBR vertex shader to modify vertex positions if 'instanced' is defined (more on this later).


```
    @Override
    protected PBRShader createShader(Renderable renderable, PBRShaderConfig config, String prefix){
        if( renderable.meshPart.mesh.isInstanced()) {
            prefix += "#define instanced\n";
        }
        config.vertexShader = Gdx.files.internal("shaders/pbr-instanced.vs.glsl").readString();
        return new MyPBRShader(renderable, config, prefix);
    }
```

The derived PBR shader passes almost everything to PBRShader, except that canRender() returns false if the shader was created for instanced renderables
and receives a non-instanced renderable or vice versa.  We want the shader with the extra prefix line mentioned before only to be used for instanced renderables.
So we use the logic in canRender() to force the use of the correct shader instance per renderable.

```
public class MyPBRShader extends PBRShader {

    private boolean isInstancedShader;

    public MyPBRShader(Renderable renderable, Config config, String prefix) {
        super(renderable, config, prefix);
        isInstancedShader = renderable.meshPart.mesh.isInstanced();
    }

    @Override
    public boolean canRender(Renderable renderable) {
        if(renderable.meshPart.mesh.isInstanced() != isInstancedShader ) {
            return false;
        }
        return super.canRender(renderable);
    }
}
```


So what are the changes made in the vertex shader?

The attribute mat4 `i_worldTrans` is defined if 'instance' is defined. This will contain the data (the transformation matrix) of the instance. 

    #if defined(instanced)
        attribute mat4 i_worldTrans;
    #endif // instanced

Then we add some conditional code to modify the vertex position vector just before it is emitted via gl_Position.  
To get the lighting correct the normal vector is multiplied by the transposed inverse of the transform matrix.

    ...
    // vec4 pos is world position of the vertex

    vec3 normalVec = a_normal;
    #if defined(instanced)
        pos *= i_worldTrans;
        normalVec = a_normal * transpose(inverse(mat3(i_worldTrans)));
    #endif

    v_position = vec3(pos.xyz) / pos.w;
    gl_Position = u_projViewTrans * pos;


The type of instance data you want to use depends on your particular application.
In this particular test the instance data uses 16 floats as transformation matrix per instance.  This allows to position, scale and rotate each instance at will:

The changes to the depth vertex shader are very similar.

Note that the built-in functions transpose() and inverse() are only available in GLSL since #version 140 (Desktop) or #version 300 ES (web).


## Platforms

- `core`: Main module with the application logic shared by all platforms.
- `lwjgl3`: Primary desktop platform using LWJGL3.
- `html`: Web platform using GWT and WebGL. Supports only Java projects.  NOT TESTED/NOT WORKING.
- `teavm`: Experimental web platform using TeaVM and WebGL.


Version 1.0

In this older version, I was not passing a full transformation matrix (16 floats) but an attribute of 4 floats to modify each instance:
- 2 floats to translate in the horizontal (XZ) plane
- 1 float to scale in the upwards direction (Y)
- 1 float to rotate around the up axis (Y)

This saves on the amount of data to transfer, but puts some constraints on instance orientation and scaling.  It turns out the frame rate does not seem very much affected
by the amount of data transfer (based on a single test).

DuckField is an updated version of my Corn Field repo, to address lighting issues.
