package com.monstrous.duckfield;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.profiling.GLErrorListener;
import com.badlogic.gdx.graphics.profiling.GLProfiler;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ScreenUtils;
import net.mgsx.gltf.loaders.gltf.GLTFLoader;
import net.mgsx.gltf.scene3d.attributes.PBRCubemapAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRTextureAttribute;
import net.mgsx.gltf.scene3d.lights.DirectionalShadowLight;
import net.mgsx.gltf.scene3d.scene.Scene;
import net.mgsx.gltf.scene3d.scene.SceneAsset;
import net.mgsx.gltf.scene3d.scene.SceneManager;
import net.mgsx.gltf.scene3d.scene.SceneSkybox;
import net.mgsx.gltf.scene3d.utils.IBLBuilder;

import java.nio.Buffer;
import java.nio.FloatBuffer;


public class Main extends ApplicationAdapter {

    public static String GLTF_FILE = "models/duckfield.gltf";

    private static final float SEPARATION_DISTANCE = 1.2f;          // min distance between instances
    private static final float AREA_LENGTH = 50.0f;                // size of the (square) field

    private static final int SHADOW_MAP_SIZE = 8192;


    private SceneManager sceneManager;
    private SceneAsset sceneAsset;
    private Scene sceneDuck;
    private PerspectiveCamera camera;
    private Cubemap diffuseCubemap;
    private Cubemap environmentCubemap;
    private Cubemap specularCubemap;
    private Texture brdfLUT;
    private SceneSkybox skybox;
    private DirectionalShadowLight light;
    private CameraInputController camController;
    private BitmapFont font;
    private SpriteBatch batch;
    private int instanceCount;
    private boolean showInstances = true;
    private boolean showDecals = false;
    private DepthBufferShader depthBufferShader;
    private Model frustumModel;
    private Vector3 lightPosition = new Vector3();
    private Vector3 lightCentre = new Vector3();
    private ModelBatch modelBatch;
    private Array<ModelInstance> instances;
    private Model arrowModel;
    private int width, height;
    private boolean showDepthBuffer = false;
    private boolean showDebug = false;
    private boolean autoRotate = true;
    private Vector3 v3 = new Vector3();
    private GLProfiler glProfiler;


    @Override
    public void create() {

        // to do OpenGL debugging
//        glProfiler = new GLProfiler(Gdx.graphics);
//        glProfiler.enable();
//        glProfiler.setListener(GLErrorListener.THROWING_LISTENER);

        Gdx.app.setLogLevel(Application.LOG_DEBUG);
        if (Gdx.gl30 == null) {
            throw new GdxRuntimeException("GLES 3.0 profile required for this programme.");
        }

        batch = new SpriteBatch();

        // setup camera
        camera = new PerspectiveCamera(50f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.1f;
        camera.far = 800f;
        camera.position.set(-5, 2.5f, -5);
        camera.lookAt(10,1.5f,10);
        camera.update();

        // create scene manager
        // but use our own shader providers
        sceneManager = new SceneManager( new MyPBRShaderProvider(), new MyPBRDepthShaderProvider() );
        sceneManager.setCamera(camera);


        camController = new CameraInputController(camera);
        Gdx.input.setInputProcessor(camController);

        // gdx-gltf set up
        //
        sceneManager.environment.set(new PBRFloatAttribute(PBRFloatAttribute.ShadowBias, 1f/512f));



        // setup light


        // set the light parameters so that your area of interest is in the shadow light frustum
        // but keep it reasonably tight to keep sharper shadows
        lightPosition = new Vector3(0,35,0);    // even though this is a directional light and is "infinitely far away", use this to set the near plane
        float farPlane = 100;
        float nearPlane = 0;
        float VP_SIZE = 70f;

        light = new DirectionalShadowLight(SHADOW_MAP_SIZE, SHADOW_MAP_SIZE).setViewport(VP_SIZE, VP_SIZE,nearPlane, farPlane);
        light.direction.set(1, -1, 1);
        light.direction.nor();
        light.color.set(Color.WHITE);
        light.intensity = 2.8f;

        // for the directional shadow light we can set the light centre which is the center of the frustum of the orthogonal camera
        // that is used to create the depth buffer.
        // calculate the centre from the light position, near and far planes and light direction
        float halfDepth = (nearPlane + farPlane)/2f;
        lightCentre.set(light.direction).scl(halfDepth).add(lightPosition);

        light.setCenter(lightCentre);           // set the centre of the frustum box

        sceneManager.environment.add(light);

        // setup quick IBL (image based lighting)
        IBLBuilder iblBuilder = IBLBuilder.createOutdoor(light);
        environmentCubemap = iblBuilder.buildEnvMap(1024);
        diffuseCubemap = iblBuilder.buildIrradianceMap(256);
        specularCubemap = iblBuilder.buildRadianceMap(10);
        iblBuilder.dispose();

        // This texture is provided by the library, no need to have it in your assets.
        brdfLUT = new Texture(Gdx.files.classpath("net/mgsx/gltf/shaders/brdfLUT.png"));

        sceneManager.setAmbientLight(0.0f);
        sceneManager.environment.set(new PBRTextureAttribute(PBRTextureAttribute.BRDFLUTTexture, brdfLUT));
        sceneManager.environment.set(PBRCubemapAttribute.createSpecularEnv(specularCubemap));
        sceneManager.environment.set(PBRCubemapAttribute.createDiffuseEnv(diffuseCubemap));

        // setup skybox
        skybox = new SceneSkybox(environmentCubemap);
        sceneManager.setSkyBox(skybox);

        sceneAsset = new GLTFLoader().load(Gdx.files.internal(GLTF_FILE));

        sceneManager.addScene( getScene(sceneAsset, "GridGround2"));            // ground plane
        sceneManager.addScene( getScene(sceneAsset, "reeds"));                  // model which is not instanced
        sceneDuck = getScene(sceneAsset, "ducky");                              // model that will be instanced
        sceneManager.addScene(sceneDuck);

        // alternative: make many model instances
 //       addCopies();

        // assumes the instance has one node,  and the meshPart covers the whole mesh
        for(int i = 0; i < sceneDuck.modelInstance.nodes.first().parts.size; i++) {
            Mesh mesh = sceneDuck.modelInstance.nodes.first().parts.get(i).meshPart.mesh;
            setupInstancedMesh(mesh);
        }


        depthBufferShader = new DepthBufferShader();
        depthBufferShader.init();

        instances = new Array<>();

        // force the light.camera to be set to the correct position and direction
        light.begin();
        light.end();

        // create a frustum model (box shape, since the camera is orthogonal) for the directional shadow light
        frustumModel = createFrustumModel(light.getCamera().frustum.planePoints);
        ModelInstance frustumInstance = new ModelInstance(frustumModel, lightPosition);

        // move frustum to world position of light camera
        Vector3 offset = new Vector3(light.getCamera().position).scl(-1);
        frustumInstance.transform.translate(offset);
        instances.add(frustumInstance);

        ModelBuilder modelBuilder = new ModelBuilder();

        // add sphere as light source
        float sz = 1.0f;
        Model ball = modelBuilder.createSphere(sz, sz, sz, 4, 4, new Material(), VertexAttributes.Usage.Position | VertexAttributes.Usage.ColorPacked);
        instances.add(new ModelInstance(ball, light.getCamera().position));

        // light direction as arrow
        Model arrow = modelBuilder.createArrow(Vector3.Zero, light.direction, new Material(), VertexAttributes.Usage.Position | VertexAttributes.Usage.ColorPacked);
        instances.add(new ModelInstance(arrow, lightPosition));

        // XYZ axis reference
        arrowModel = modelBuilder.createXYZCoordinates(15f, new Material(), VertexAttributes.Usage.Position | VertexAttributes.Usage.ColorPacked);
        instances.add(new ModelInstance(arrowModel, Vector3.Zero));

        modelBatch = new ModelBatch();
    }

    // had to copy this from DirectionalShadowLight because it is protected
    private void validate(Camera cam, Vector3 center, Vector3 direction){
        float halfDepth = cam.near + 0.5f * (cam.far - cam.near);
        cam.position.set(direction).scl(-halfDepth).add(center);
        cam.direction.set(direction).nor();
        cam.normalizeUp();
        cam.update();
    }

    private Scene getScene( SceneAsset asset, String name ){
        Scene scene = new Scene(asset.scene, name);
        if(scene.modelInstance.nodes.size == 0) {
            Gdx.app.error("GLTF load error: node not found", name);
            Gdx.app.exit();
        }
        return scene;
    }


    // without instancing you have to generate lots of scenes
    private void addCopies() {
        // generate instance data

        // generate a random poisson distribution of instances over a rectangular area, meaning instances are never too close together
        PoissonDistribution poisson = new PoissonDistribution();
        Rectangle area = new Rectangle(1, 1, AREA_LENGTH, AREA_LENGTH);
        Array<Vector2> points = poisson.generatePoissonDistribution(SEPARATION_DISTANCE, area);
        instanceCount = points.size;

        for(Vector2 point: points) {
            sceneDuck = getScene(sceneAsset, "ducky");
            sceneDuck.modelInstance.transform.translate(point.x, 0, point.y);
            sceneManager.addScene(sceneDuck);
        }
    }

    private void setupInstancedMesh( Mesh mesh ) {

        // generate instance data

        // generate a random poisson distribution of instances over a rectangular area, meaning instances are never too close together
        PoissonDistribution poisson = new PoissonDistribution();
        Rectangle area = new Rectangle(1, 1, AREA_LENGTH, AREA_LENGTH);
        Array<Vector2> points = poisson.generatePoissonDistribution(SEPARATION_DISTANCE, area);
        instanceCount = points.size;

        // add 4 floats per instance
        mesh.enableInstancedRendering(true, instanceCount, new VertexAttribute(VertexAttributes.Usage.Position, 4, "i_offset")  );

        // Create offset FloatBuffer that will contain instance data to pass to shader
        FloatBuffer offsets = BufferUtils.newFloatBuffer(instanceCount * 4);
//        Gdx.app.setLogLevel(Application.LOG_DEBUG);
//        Gdx.app.log("FloatBuffer: isDirect()",  "" + offsets.isDirect());  // false = teaVM for now
//        Gdx.app.log("Application: Type()",  "" + Gdx.app.getType());

        // fill instance data buffer
        for(Vector2 point: points) {
                float angle = MathUtils.random(0.0f, (float)Math.PI*2.0f);      // random rotation around Y (up) axis
                float scaleY =MathUtils.random(0.8f, 1.2f);                    // vary scale in up direction +/- 20%

                offsets.put(new float[] {point.x, scaleY, point.y, angle });     // x, y-scale, z, y-rotation
        }

        ((Buffer)offsets).position(0);
        mesh.setInstanceData(offsets);
    }

    // from libgdx tests
    public static Model createFrustumModel (final Vector3... p) {
        ModelBuilder builder = new ModelBuilder();
        builder.begin();
        MeshPartBuilder mpb = builder.part("", GL20.GL_LINES, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal,
            new Material(new ColorAttribute(ColorAttribute.Diffuse, Color.WHITE)));
        mpb.vertex(p[0].x, p[0].y, p[0].z, 0, 0, 1, p[1].x, p[1].y, p[1].z, 0, 0, 1, p[2].x, p[2].y, p[2].z, 0, 0, 1, p[3].x,
            p[3].y, p[3].z, 0, 0, 1, // near
            p[4].x, p[4].y, p[4].z, 0, 0, -1, p[5].x, p[5].y, p[5].z, 0, 0, -1, p[6].x, p[6].y, p[6].z, 0, 0, -1, p[7].x, p[7].y,
            p[7].z, 0, 0, -1);
        mpb.index((short)0, (short)1, (short)1, (short)2, (short)2, (short)3, (short)3, (short)0);
        mpb.index((short)4, (short)5, (short)5, (short)6, (short)6, (short)7, (short)7, (short)4);
        mpb.index((short)0, (short)4, (short)1, (short)5, (short)2, (short)6, (short)3, (short)7);
        return builder.end();
    }



    @Override
    public void render() {
        if(Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)){
            showInstances = !showInstances;
            if(!showInstances)
                sceneManager.removeScene(sceneDuck);
            else
                sceneManager.addScene(sceneDuck);
        }
        if(Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)){
            autoRotate = !autoRotate;
        }
        if(Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)){
            showDepthBuffer = !showDepthBuffer;
        }
        if(Gdx.input.isKeyJustPressed(Input.Keys.NUM_4)){
            showDebug = !showDebug;
        }

        if(autoRotate) {
            // rotating the master model instance will rotate all instances
            sceneDuck.modelInstance.transform.rotate(Vector3.Y, Gdx.graphics.getDeltaTime() * 45f);
        }

        camController.update();
        sceneManager.update(Gdx.graphics.getDeltaTime());

        // if you want the shadow light to stay close to the camera
//        light.setCenter(camera.position);

        ScreenUtils.clear(Color.TEAL, true);

        sceneManager.render();
        // if you prefer to do the passes separately:
//        sceneManager.renderShadows();
//        sceneManager.renderColors();


        if(showDepthBuffer)
            renderBuffer(light.getFrameBuffer(), depthBufferShader.program, 0, 0, height/2, height/2);


        if(showDebug) {
            modelBatch.begin(sceneManager.camera);
            modelBatch.render(instances);
            modelBatch.end();
        }


        batch.begin();
        font.draw(batch, "Instanced rendering demo (1: toggle instances, 2: rotate instances, 3: depth buffer, 4: show shadow frustum)", 20, 110);
        font.draw(batch, "Instances: "+instanceCount, 20, 80);
        font.draw(batch, "Vertices/instance: "+countVertices(sceneDuck.modelInstance), 20, 50);
        font.draw(batch, "FPS: "+Gdx.graphics.getFramesPerSecond(), 20, 20);

        batch.end();
    }

    void renderBuffer(FrameBuffer fbo, ShaderProgram prg, int x, int y, int w, int h ) {
        batch.begin();
        batch.setShader(prg);						// post-processing shader
        batch.draw(fbo.getColorBufferTexture(), x, y, w, h,0,0,1,1);    // draw frame buffer as screen filling texture
        batch.end();
        batch.setShader(null);
    }



    private int countVertices(ModelInstance instance){
        int count = 0;
        for(int i = 0; i < instance.nodes.first().parts.size; i++){
            count += instance.nodes.first().parts.get(i).meshPart.mesh.getNumVertices();
        }
        return count;
    }

    @Override
    public void resize(int width, int height) {
        // Resize your screen here. The parameters represent the new window size.
        sceneManager.updateViewport(width, height);
        this.width = width;
        this.height = height;
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);  // to ensure the fbo is rendered to the full window after a resize
        if(font != null)
            font.dispose();
        font = new BitmapFont();
    }


    @Override
    public void dispose() {

        sceneManager.dispose();
        sceneAsset.dispose();
        environmentCubemap.dispose();
        diffuseCubemap.dispose();
        specularCubemap.dispose();
        brdfLUT.dispose();
        skybox.dispose();
        arrowModel.dispose();
        frustumModel.dispose();
        modelBatch.dispose();
        font.dispose();
        batch.dispose();
    }
}
