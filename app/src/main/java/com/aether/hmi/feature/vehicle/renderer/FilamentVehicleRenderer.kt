package com.aether.hmi.feature.vehicle.renderer

import android.annotation.SuppressLint
import android.content.Context
import android.app.ActivityManager
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.TextureView
import com.aether.hmi.domain.VehicleControlsState
import com.aether.hmi.domain.VehiclePart
import com.aether.hmi.domain.TurnSignal
import com.aether.hmi.feature.vehicle.VehicleCameraPose
import com.aether.hmi.feature.vehicle.ScenePoint
import com.aether.hmi.feature.vehicle.VehicleSceneFocus
import com.aether.hmi.feature.vehicle.VehicleLampPolicy
import com.aether.hmi.feature.vehicle.VehicleBatteryAppearance
import com.aether.hmi.feature.vehicle.VehicleBatteryAnimation
import com.aether.hmi.feature.vehicle.VehicleShadowPolicy
import org.json.JSONObject
import com.aether.hmi.feature.vehicle.VehicleSceneMath
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.Texture
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.tan
import kotlin.math.atan2
import kotlin.math.asin
import kotlin.math.sqrt
import kotlin.math.PI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface VehicleModelStatus {
    data class Loading(val percent: Int? = null) : VehicleModelStatus
    data object Ready : VehicleModelStatus
    data object Failed : VehicleModelStatus
}

/** Owns one retained native scene; viewports attach independently. Native calls stay on main. */
internal class FilamentVehicleRenderer(
    private val context: Context,
    private val onStatus: (VehicleModelStatus) -> Unit,
    private val onCameraPose: (VehicleCameraPose) -> Unit,
) : Choreographer.FrameCallback, UiHelper.RendererCallback {
    private val scope = CoroutineScope(Job() + Dispatchers.Main.immediate)
    private val choreographer = Choreographer.getInstance()
    private val handler = Handler(Looper.getMainLooper())
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private var engine: Engine? = null
    private var renderer: Renderer? = null
    private var scene: Scene? = null
    private var view: View? = null
    private var camera: Camera? = null
    private var cameraEntity = 0
    private var swapChain: SwapChain? = null
    private var materials: UbershaderProvider? = null
    private var assetLoader: AssetLoader? = null
    private var resourceLoader: ResourceLoader? = null
    private var asset: FilamentAsset? = null
    private var indirect: IndirectLight? = null
    private var reflections: Texture? = null
    private val lightEntities = mutableListOf<Int>()
    private val surfaceMaterials = mutableMapOf<Long, SurfaceMaterial>()
    private val namedRenderables = mutableMapOf<String, Int>()
    private val nativeLampEntities = mutableMapOf<String, Int>()
    private val readyEntities = IntArray(64)
    private val parts = mutableMapOf<VehiclePart, MovingPart>()
    private val entityParts = mutableMapOf<Int, VehiclePart>()
    private var controls = VehicleControlsState()
    private var texture: TextureView? = null
    private var onPartTap: ((VehiclePart) -> Unit)? = null
    private var attachmentGeneration = 0L
    private var initialized = false
    private var active = false
    private var disposed = false
    private var failed = false
    private var posted = false
    private var loading = true
    private var lastProgress = -1
    private var lastFrame = 0L
    private var loadingActiveNanos = 0L
    private var width = 1
    private var height = 1
    private var pickFrames = 0
    private var cameraDirty = true
    private var yaw = 38.0
    private var pitch = 8.0
    private var zoom = 1.0
    private var reducedMotion = false
    private var loadJob: Job? = null
    private var focus = VehicleSceneFocus.Overview
    private var charging = false
    private var batteryPercent: Int? = null
    private var batteryAnimationSeconds = 0.0
    private var batteryAppearance = VehicleBatteryAppearance.fromPercent(null)
    private var stageLeft = 0f
    private var stageWidth = 1f
    private var stageBottomInset = 0f
    private var scenery: VehicleScenery? = null
    private var surfaceDefinitions: Map<String, List<SurfaceDefaults>> = emptyMap()
    private var activeMaterialVariant = 0
    private var xrayFraction = 0f
    private var currentPose: VehicleCameraPose? = null
    private var lightingDirty = true
    private var previousBlink = true
    private var previousTrunkOpen = false
    private var signalSeconds = 0.0

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoom = (zoom / detector.scaleFactor).coerceIn(1.0, 1.35)
            cameraDirty = true
            requestFrame()
            return true
        }
    })
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (!scaleDetector.isInProgress && e2.pointerCount == 1) {
                yaw = (yaw - distanceX * .28) % 360.0
                pitch = VehicleSceneMath.dragPitch(focus, pitch, distanceY)
                cameraDirty = true
                requestFrame()
            }
            return true
        }
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val attached = texture ?: return false
            attached.performClick()
            if (!loading && controls.available) {
                // Do not retain the old TextureView or its callback in a native pick request.
                val generation = attachmentGeneration
                view?.pick(e.x.toInt().coerceIn(0, width - 1), (height - 1 - e.y.toInt()).coerceIn(0, height - 1), handler) { result ->
                    if (!disposed && !failed && texture != null && generation == attachmentGeneration) {
                        entityParts[result.renderable]?.let { onPartTap?.invoke(it) }
                    }
                }
                pickFrames = 8
                requestFrame()
            }
            return true
        }
    })

    fun initialize() {
        checkMainThread()
        if (initialized || disposed) return
        initialized = true
        try {
            check(context.getSystemService(ActivityManager::class.java).deviceConfigurationInfo.reqGlEsVersion >= 0x30000) {
                "OpenGL ES 3.0 is required for the vehicle renderer"
            }
            Gltfio.init()
            val e = Engine.create(Engine.Backend.OPENGL).also { engine = it }
            renderer = e.createRenderer().apply {
                clearOptions = Renderer.ClearOptions().apply { clear = true; clearColor = floatArrayOf(0f, 0f, 0f, 0f) }
            }
            scene = e.createScene()
            cameraEntity = EntityManager.get().create()
            camera = e.createCamera(cameraEntity).apply { setExposure(16f, 1f / 125f, 100f) }
            view = e.createView().apply {
                scene = this@FilamentVehicleRenderer.scene
                camera = this@FilamentVehicleRenderer.camera
                blendMode = View.BlendMode.TRANSLUCENT
                isTransparentPickingEnabled = true
                antiAliasing = View.AntiAliasing.FXAA
                // Two samples smooth silhouettes without temporal history or a 4x target.
                multiSampleAntiAliasingOptions = multiSampleAntiAliasingOptions.apply {
                    enabled = true; sampleCount = 2; customResolve = false
                }
                setShadowingEnabled(true)
                setShadowType(View.ShadowType.PCF)
                ambientOcclusionOptions = ambientOcclusionOptions.apply {
                    enabled = true; radius = .35f; intensity = .8f; power = 1.2f
                    resolution = .5f; quality = View.QualityLevel.LOW
                }
                bloomOptions = bloomOptions.apply {
                    enabled = true; strength = .035f; resolution = 256; levels = 4
                    threshold = true; lensFlare = false
                }
            }
            materials = UbershaderProvider(e)
            assetLoader = AssetLoader(e, materials!!, EntityManager.get())
            resourceLoader = ResourceLoader(e)
            indirect = IndirectLight.Builder().irradiance(1, floatArrayOf(.8f, .88f, 1f)).intensity(22_000f).build(e)
            scene!!.indirectLight = indirect
            addLight(LightManager.Type.SUN, 45_000f, floatArrayOf(1f, .91f, .80f), floatArrayOf(-.5f, -.8f, -.6f), castShadows = true)
            addLight(LightManager.Type.DIRECTIONAL, 12_000f, floatArrayOf(.70f, .82f, 1f), floatArrayOf(.7f, -.3f, .4f))
            uiHelper.isOpaque = false
            uiHelper.renderCallback = this
            loadJob = scope.launch {
                try {
                    val prepared = withContext(Dispatchers.IO) {
                        val bytes = context.assets.open("models/GenericSUV.glb").use { it.readBytes() }
                        val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply { put(bytes); flip() }
                        PreparedScene(model, VehicleEnvironment.read(context.resources), readSurfaceDefaults(bytes), VehicleScenery.read(context.resources))
                    }
                    if (disposed) return@launch
                    createReflectionEnvironment(prepared.environment)
                    val loaded = requireNotNull(assetLoader!!.createAsset(prepared.model)) { "Invalid bundled vehicle model" }
                    asset = loaded
                    surfaceDefinitions = prepared.definitions
                    check(loaded.instance.materialVariantNames.toList() == listOf("Normal", "BatteryXray")) { "Missing vehicle material variants" }
                    bindParts(loaded, surfaceDefinitions)
                    scenery = VehicleScenery(e, scene!!, materials!!)
                    scenery!!.begin(prepared.scenery)
                    updateBatteryGauge()
                    check(resourceLoader!!.asyncBeginLoad(loaded)) { "Unable to load vehicle textures" }
                    loaded.releaseSourceData()
                    applySurfaces()
                    applyLighting(true, false)
                    requestFrame()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    fail(error)
                }
            }
        } catch (error: LinkageError) {
            fail(error)
        } catch (error: Exception) {
            fail(error)
        }
    }

    @SuppressLint("ClickableViewAccessibility") // performClick plus named Compose actions accompany picking.
    fun attach(texture: TextureView, onPartTap: (VehiclePart) -> Unit) {
        checkMainThread()
        if (disposed || failed) return
        if (this.texture === texture) {
            this.onPartTap = onPartTap
            return
        }
        this.texture?.let(::detach)
        attachmentGeneration++
        this.texture = texture
        this.onPartTap = onPartTap
        texture.setOnTouchListener { touchedView, event ->
            if (this.texture === touchedView) onTouch(event) else false
        }
        try {
            uiHelper.attachTo(texture)
        } catch (error: Exception) {
            fail(error)
        }
    }

    /** Releases only the viewport. GLB, materials, environment, and camera remain cached. */
    fun detach(texture: TextureView) {
        checkMainThread()
        if (this.texture !== texture) return
        setActive(false)
        attachmentGeneration++
        this.texture = null
        onPartTap = null
        texture.setOnTouchListener(null)
        texture.parent?.requestDisallowInterceptTouchEvent(false)
        uiHelper.detach()
    }

    private fun addLight(type: LightManager.Type, intensity: Float, color: FloatArray, direction: FloatArray?, position: FloatArray? = null, castShadows: Boolean = false): Int {
        val e = checkNotNull(engine)
        val entity = EntityManager.get().create()
        lightEntities += entity
        val builder = LightManager.Builder(type).color(color[0], color[1], color[2]).intensity(intensity).castShadows(castShadows)
        if (castShadows) builder.shadowOptions(LightManager.ShadowOptions().apply {
            mapSize = 1024; shadowCascades = 1; shadowFar = 20f
            constantBias = .001f; normalBias = .35f
            stable = true; screenSpaceContactShadows = true
        })
        direction?.let { builder.direction(it[0], it[1], it[2]) }
        position?.let { builder.position(it[0], it[1], it[2]).falloff(3f) }
        builder.build(e, entity)
        scene!!.addEntity(entity)
        return entity
    }

    private fun bindParts(loaded: FilamentAsset, definitions: Map<String, List<SurfaceDefaults>>) {
        val e = checkNotNull(engine)
        val names = mapOf("Door_FL" to VehiclePart.FrontLeftDoor, "Door_FR" to VehiclePart.FrontRightDoor,
            "Door_BL" to VehiclePart.RearLeftDoor, "Door_BR" to VehiclePart.RearRightDoor, "Door_Trunk" to VehiclePart.Trunk,
            "BONNET" to VehiclePart.Bonnet, "SUNROOF" to VehiclePart.Sunroof, "CHARGE_PORT" to VehiclePart.ChargePort)
        val byName = loaded.entities.associateBy { loaded.getName(it) }
        for ((name, entity) in byName) {
            names[name]?.let { part ->
                val instance = e.transformManager.getInstance(entity)
                val rest = FloatArray(16)
                e.transformManager.getTransform(instance, rest)
                parts[part] = MovingPart(instance, rest)
            }
            val instance = e.renderableManager.getInstance(entity)
            if (instance != 0 && name != null) {
                namedRenderables[name] = instance
                val solid = definitions[name].orEmpty().any { it.opaque }
                // Glass, glow cards and optional LEDs must not cast opaque rectangles.
                e.renderableManager.setCastShadows(instance, VehicleShadowPolicy.castsShadows(name, solid))
                // The coarse sun map causes diagonal acne on grazing paint surfaces.
                // Keep real floor shadows and local AO; paint still casts onto the floor.
                e.renderableManager.setReceiveShadows(instance, !VehicleShadowPolicy.isInterior(name) &&
                    !definitions[name].orEmpty().any { it.painted })
                // Picking a reparented accessory operates its moving panel too.
                var parent = entity
                while (parent != 0) {
                    val part = names[loaded.getName(parent)]
                    if (part != null) { entityParts[entity] = part; break }
                    parent = e.transformManager.getParent(e.transformManager.getInstance(parent))
                }
            }
            if (e.lightManager.hasComponent(entity) && name != null) {
                nativeLampEntities[name] = entity
                e.lightManager.setIntensity(e.lightManager.getInstance(entity), 0f)
                scene!!.addEntity(entity)
            }
        }
        check(parts.size == VehiclePart.entries.size) { "Vehicle movable parts are missing" }
        bindSurfaces(loaded, definitions)
    }

    private fun bindSurfaces(loaded: FilamentAsset, definitions: Map<String, List<SurfaceDefaults>>) {
        val e = engine ?: return
        surfaceMaterials.clear()
        for (entity in loaded.renderableEntities) {
            val name = loaded.getName(entity) ?: continue
            val instance = e.renderableManager.getInstance(entity)
            if (instance == 0) continue
            val defaults = requireNotNull(definitions[name]) { "Missing material metadata for $name" }
            repeat(e.renderableManager.getPrimitiveCount(instance)) { primitive ->
                val material = e.renderableManager.getMaterialInstanceAt(instance, primitive)
                val original = defaults[primitive]
                val previous = surfaceMaterials[material.nativeObject]
                // The source reuses a material instance for many LED nodes. Aggregate
                // their roles so traversal order cannot fade shared indicator materials.
                surfaceMaterials[material.nativeObject] = SurfaceMaterial(material, original,
                    battery = previous?.battery == true || name == "DRIVETRAIN_BATTERY",
                    indicator = previous?.indicator == true || VehicleLampPolicy.isIndicatorNode(name),
                    emissionGain = maxOf(previous?.emissionGain ?: 1f, VehicleLampPolicy.emissionGain(name)))
            }
        }
    }

    private fun selectMaterialVariant(xray: Boolean) {
        val loaded = asset ?: return
        val variant = if (xray) 1 else 0
        if (activeMaterialVariant == variant) return
        loaded.instance.applyMaterialVariant(variant)
        activeMaterialVariant = variant
        // Variants replace primitive material instances, not transforms or animation state.
        bindSurfaces(loaded, surfaceDefinitions)
        applySurfaces()
    }

    /** Read the bundled glTF's own factors; restoring x-ray must preserve paint/glass/texture defaults. */
    private fun readSurfaceDefaults(bytes: ByteArray): Map<String, List<SurfaceDefaults>> {
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val jsonLength = header.getInt(12)
        val document = JSONObject(String(bytes, 20, jsonLength, Charsets.UTF_8))
        val materials = document.getJSONArray("materials")
        val defaults = List(materials.length()) { index ->
            val material = materials.getJSONObject(index)
            val rgba = material.optJSONObject("pbrMetallicRoughness")?.optJSONArray("baseColorFactor")
            val emission = material.optJSONArray("emissiveFactor")
            val strength = material.optJSONObject("extensions")
                ?.optJSONObject("KHR_materials_emissive_strength")?.optDouble("emissiveStrength", 1.0)?.toFloat() ?: 1f
            SurfaceDefaults(FloatArray(4) { rgba?.optDouble(it, 1.0)?.toFloat() ?: 1f },
                FloatArray(3) { (emission?.optDouble(it, 0.0)?.toFloat() ?: 0f) * strength },
                opaque = material.optString("alphaMode", "OPAQUE") != "BLEND",
                painted = material.optString("name") == "Generic_body_paint")
        }
        val meshes = document.getJSONArray("meshes")
        val nodes = document.getJSONArray("nodes")
        return buildMap {
            repeat(nodes.length()) { index ->
                val node = nodes.getJSONObject(index)
                if (node.has("mesh")) {
                    val primitives = meshes.getJSONObject(node.getInt("mesh")).getJSONArray("primitives")
                    put(node.getString("name"), List(primitives.length()) { defaults[primitives.getJSONObject(it).getInt("material")] })
                }
            }
        }
    }

    private fun createReflectionEnvironment(buffer: ByteBuffer) {
        val e = checkNotNull(engine)
        val cubemap = Texture.Builder().width(VehicleEnvironment.faceSize).height(VehicleEnvironment.faceSize)
            .levels(VehicleEnvironment.levels).sampler(Texture.Sampler.SAMPLER_CUBEMAP)
            .format(Texture.InternalFormat.RGB16F).build(e).also { reflections = it }
        cubemap.generatePrefilterMipmap(e, Texture.PixelBufferDescriptor(buffer, Texture.Format.RGB, Texture.Type.FLOAT),
            VehicleEnvironment.faceOffsets, Texture.PrefilterOptions().apply { sampleCount = 64; mirror = false })
        val environment = IndirectLight.Builder().reflections(cubemap)
            .irradiance(1, floatArrayOf(.8f, .88f, 1f)).intensity(22_000f).build(e)
        scene!!.indirectLight = environment
        indirect?.let(e::destroyIndirectLight)
        indirect = environment
    }

    fun updateControls(next: VehicleControlsState) {
        if (disposed || controls == next) return
        if (controls.effectiveTurnSignal != next.effectiveTurnSignal) {
            signalSeconds = 0.0
            // A previously idle scene must begin at the new signal's ON phase.
            lastFrame = 0L
        }
        controls = next
        lightingDirty = true
        cameraDirty = true
        requestFrame()
    }

    fun updateStageViewport(leftFraction: Float, widthFraction: Float, bottomInsetPixels: Float) {
        val left = leftFraction.coerceIn(0f, .8f)
        val span = widthFraction.coerceIn(.15f, 1f-left)
        if (stageLeft == left && stageWidth == span && stageBottomInset == bottomInsetPixels) return
        stageLeft = left; stageWidth = span; stageBottomInset = bottomInsetPixels
        cameraDirty = true
        requestFrame()
    }

    fun updatePresentation(nextFocus: VehicleSceneFocus, isCharging: Boolean, batteryPercent: Int?) {
        if (disposed || (focus == nextFocus && charging == isCharging && this.batteryPercent == batteryPercent)) return
        if (this.batteryPercent != batteryPercent) {
            this.batteryPercent = batteryPercent
            batteryAnimationSeconds = 0.0
            batteryAppearance = VehicleBatteryAppearance.fromPercent(batteryPercent)
            applySurfaces()
        }
        if (focus != nextFocus) {
            focus = nextFocus
            val preset = VehicleSceneMath.focusOrbit(focus)
            yaw = preset.first
            pitch = preset.second
            zoom = 1.0
            cameraDirty = true
        }
        if (charging != isCharging) {
            charging = isCharging
            batteryAnimationSeconds = 0.0
            lightingDirty = true
            if (!charging) {
                // Restore depth-writing solids immediately when the battery view ends.
                // Fading near-opaque BLEND shells exposes the drivetrain during the exit.
                xrayFraction = 0f
                selectMaterialVariant(false)
                applySurfaces()
            }
        }
        updateBatteryGauge()
        requestFrame()
    }

    private fun updateBatteryGauge() {
        val fill = VehicleBatteryAnimation.fillFraction(batteryAnimationSeconds,
            batteryAppearance.chargeFraction, reducedMotion)
        scenery?.updateBatteryGauge(charging, fill)
    }

    fun setReducedMotion(value: Boolean) {
        if (reducedMotion != value) {
            reducedMotion = value
            updateBatteryGauge()
            cameraDirty = true
            requestFrame()
        }
    }

    private fun applyLighting(blinkOn: Boolean, trunkOpen: Boolean) {
        val e = engine ?: return
        namedRenderables.forEach { (name, instance) ->
            val visible = VehicleLampPolicy.visible(name, controls, charging, blinkOn, trunkOpen)
            e.renderableManager.setLayerMask(instance, 0xff, if (visible) 1 else 0)
        }
        nativeLampEntities.forEach { (name, entity) ->
            val visible = VehicleLampPolicy.visible(name, controls, charging, blinkOn, trunkOpen)
            // Keep the source's real spotlights and their authored transforms. Never boost every lens.
            e.lightManager.setIntensity(e.lightManager.getInstance(entity), if (!visible) 0f else if (name.startsWith("HEADLAMP")) 240f else 12f)
        }
        lightingDirty = false
    }

    private fun applySurfaces() {
        surfaceMaterials.values.forEach { (material, original, battery, indicator, gain) ->
            val rgba = original.rgba
            val ghost = if (battery || indicator) 1f else 1f - .85f * xrayFraction
            if (material.material.hasParameter("baseColorFactor")) {
                fun color(channel: Int, target: Float) = if (battery) rgba[channel] + (target-rgba[channel])*xrayFraction else rgba[channel]
                material.setParameter("baseColorFactor", color(0, batteryAppearance.red),
                    color(1, batteryAppearance.green), color(2, batteryAppearance.blue), rgba[3] * ghost)
            }
            if (material.material.hasParameter("emissiveFactor")) {
                val emission = original.emission
                // Large pack surfaces need a small green lift, not the small LEDs' gain.
                material.setParameter("emissiveFactor", emission[0] * gain + if (battery) batteryAppearance.emissionRed*xrayFraction else 0f,
                    emission[1] * gain + if (battery) batteryAppearance.emissionGreen*xrayFraction else 0f,
                    emission[2] * gain + if (battery) batteryAppearance.emissionBlue*xrayFraction else 0f)
            }
        }
    }

    fun rotate(degrees: Double) { yaw = (yaw + degrees) % 360.0; cameraDirty = true; requestFrame() }
    fun changeZoom(multiplier: Double) { zoom = (zoom * multiplier).coerceIn(1.0, 1.35); cameraDirty = true; requestFrame() }
    fun resetCamera() {
        val preset = VehicleSceneMath.focusOrbit(focus)
        yaw = preset.first; pitch = preset.second; zoom = 1.0; cameraDirty = true; requestFrame()
    }

    fun onTouch(event: MotionEvent): Boolean {
        val attached = texture ?: return false
        if (disposed || failed || !active) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) attached.parent?.requestDisallowInterceptTouchEvent(true)
        scaleDetector.onTouchEvent(event)
        gestures.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            attached.parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    fun setActive(value: Boolean) {
        checkMainThread()
        active = value && texture != null && !disposed && !failed
        if (active) { lastFrame = 0L; requestFrame() }
        else { choreographer.removeFrameCallback(this); posted = false }
    }

    private fun requestFrame() {
        if (!disposed && !failed && active && !posted && uiHelper.isReadyToRender) {
            posted = true
            choreographer.postFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        posted = false
        if (disposed || failed || !active || !uiHelper.isReadyToRender) return
        if (lastFrame != 0L && frameTimeNanos - lastFrame < 32_000_000L) { requestFrame(); return }
        try {
            val e = engine ?: return
            val a = asset
            resourceLoader?.asyncUpdateLoad()
            if (a != null) {
                var count = a.popRenderables(readyEntities)
                while (count > 0) {
                    scene!!.addEntities(readyEntities.copyOf(count))
                    count = a.popRenderables(readyEntities)
                }
            }
            val sceneryProgress = scenery?.update() ?: 0f
            val progress = if (a == null) 0 else (minOf(resourceLoader?.asyncGetLoadProgress() ?: 0f, sceneryProgress) * 100).toInt().coerceIn(0, 100)
            if (loading && progress != lastProgress) { lastProgress = progress; onStatus(VehicleModelStatus.Loading(progress)) }
            val dt = if (lastFrame == 0L) 1f / 30f else ((frameTimeNanos - lastFrame) / 1_000_000_000f).coerceIn(0f, .1f)
            if (loading) loadingActiveNanos += (dt * 1_000_000_000L).toLong()
            var moving = false
            for ((part, transform) in parts) {
                val target = if (part in controls.openParts) 1f else 0f
                val fraction = if (reducedMotion || abs(target - transform.fraction) < .001f) target
                    else transform.fraction + (target - transform.fraction) * (1f - exp(-9f * dt))
                if (fraction != transform.fraction) {
                    transform.fraction = fraction
                    val hinge = VehicleSceneMath.hingeMatrix(part, fraction)
                    Matrix.multiplyMM(transform.current, 0, transform.rest, 0, hinge, 0)
                    cameraDirty = true
                    e.transformManager.setTransform(transform.instance, transform.current)
                }
                if (fraction != target) moving = true
            }
            val xrayTarget = if (charging) 1f else 0f
            selectMaterialVariant(charging || xrayFraction > 0f)
            val nextXray = if (reducedMotion || abs(xrayTarget - xrayFraction) < .001f) xrayTarget
                else xrayFraction + (xrayTarget - xrayFraction) * (1f - exp(-8f * dt))
            if (xrayFraction != nextXray) {
                xrayFraction = nextXray
                selectMaterialVariant(charging || xrayFraction > 0f)
                applySurfaces()
            }
            if (charging) {
                if (!loading && !reducedMotion) batteryAnimationSeconds += dt.toDouble()
                updateBatteryGauge()
            }
            signalSeconds += if (lastFrame == 0L) 0.0 else (frameTimeNanos-lastFrame)/1_000_000_000.0
            val blinkOn = VehicleLampPolicy.blinkOn(signalSeconds)
            val trunkOpen = (parts[VehiclePart.Trunk]?.fraction ?: 0f) > .01f
            if (lightingDirty || blinkOn != previousBlink || trunkOpen != previousTrunkOpen) applyLighting(blinkOn, trunkOpen)
            previousBlink = blinkOn
            previousTrunkOpen = trunkOpen
            if (cameraDirty) {
                val opening = controls.openParts + parts.filterValues { it.fraction > 0f }.keys
                val fitWidth = (width*stageWidth).toInt().coerceAtLeast(1)
                val fitHeight = (height-stageBottomInset).toInt().coerceAtLeast(1)
                val target = VehicleSceneMath.cameraPose(fitWidth, fitHeight, yaw, pitch, zoom, focus, opening)
                val old = currentPose
                val amount = if (old == null || reducedMotion) 1.0 else (1.0 - exp(-10.0 * dt))
                val dx = (old?.eye?.x ?: target.eye.x) - (old?.target?.x ?: target.target.x)
                val dy = (old?.eye?.y ?: target.eye.y) - (old?.target?.y ?: target.target.y)
                val dz = (old?.eye?.z ?: target.eye.z) - (old?.target?.z ?: target.target.z)
                val radius = sqrt(dx*dx+dy*dy+dz*dz).coerceAtLeast(.001)
                val oldYaw = atan2(dx,dz)*180.0/PI
                val oldPitch = asin((dy/radius).coerceIn(-1.0,1.0))*180.0/PI
                val deltaYaw = ((yaw-oldYaw)%360.0+540.0)%360.0-180.0
                fun blend(a: ScenePoint, b: ScenePoint) = ScenePoint(a.x+(b.x-a.x)*amount, a.y+(b.y-a.y)*amount, a.z+(b.z-a.z)*amount)
                fun difference(a: ScenePoint, b: ScenePoint) = abs(a.x-b.x)+abs(a.y-b.y)+abs(a.z-b.z)
                val settled = old == null || abs(deltaYaw)+abs(oldPitch-pitch)+difference(old.target,target.target) < .001
                // Follow an orbit and refit every intermediate angle; a Cartesian eye lerp cuts across the car.
                val pose = if (settled || amount == 1.0) target else VehicleSceneMath.cameraPose(
                    fitWidth, fitHeight, VehicleSceneMath.orbitStep(oldYaw,yaw,amount), oldPitch+(pitch-oldPitch)*amount,
                    zoom, focus, opening, targetOverride=blend(old!!.target,target.target))
                currentPose = pose
                val pixelAtNear = 2.0*pose.near*tan(pose.verticalFovDegrees*PI/360.0)/fitHeight
                val centreX = width*(stageLeft+stageWidth/2.0)
                val centreY = fitHeight/2.0
                camera!!.setProjection(Camera.Projection.PERSPECTIVE, -centreX*pixelAtNear,
                    (width-centreX)*pixelAtNear, -(height-centreY)*pixelAtNear, centreY*pixelAtNear, pose.near, pose.far)
                camera!!.lookAt(pose.eye.x, pose.eye.y, pose.eye.z, pose.target.x, pose.target.y, pose.target.z, 0.0, 1.0, 0.0)
                onCameraPose(pose)
                cameraDirty = !settled && amount != 1.0
            }
            val chain = swapChain ?: return
            val rendered = renderer!!.beginFrame(chain, frameTimeNanos)
            if (rendered) { renderer!!.render(view!!); renderer!!.endFrame() }
            lastFrame = frameTimeNanos
            if (loading && a != null && progress == 100 && rendered) { loading = false; onStatus(VehicleModelStatus.Ready) }
            if (loading && loadingActiveNanos > 90_000_000_000L) { fail(IllegalStateException("Model loading timed out")); return }
            if (pickFrames > 0) pickFrames--
            val batteryAnimating = charging && !reducedMotion && (batteryAppearance.chargeFraction ?: 0f) > 0f
            if (loading || moving || cameraDirty || xrayFraction != xrayTarget || batteryAnimating ||
                controls.effectiveTurnSignal != TurnSignal.Off || pickFrames > 0 || !rendered) requestFrame()
        } catch (error: Exception) { fail(error) }
    }

    override fun onNativeWindowChanged(surface: Surface) {
        if (disposed || failed || texture == null) return
        val e = engine ?: return
        swapChain?.let(e::destroySwapChain)
        swapChain = e.createSwapChain(surface, uiHelper.swapChainFlags)
        requestFrame()
    }

    override fun onDetachedFromSurface() {
        val e = engine ?: return
        swapChain?.let { e.destroySwapChain(it); e.flushAndWait() }
        swapChain = null
        choreographer.removeFrameCallback(this)
        posted = false
    }

    override fun onResized(width: Int, height: Int) {
        if (disposed || width <= 0 || height <= 0) return
        this.width = width
        this.height = height
        view?.viewport = Viewport(0, 0, width, height)
        cameraDirty = true
        requestFrame()
    }

    private fun fail(error: Throwable) {
        if (disposed || failed) return
        Log.e("AetherVehicle", "Vehicle renderer unavailable", error)
        failed = true
        choreographer.removeFrameCallback(this)
        posted = false
        onStatus(VehicleModelStatus.Failed)
        handler.post { dispose() }
    }

    fun dispose() {
        checkMainThread()
        if (disposed) return
        // UiHelper drops its TextureView handler and native-window reference on detach.
        texture?.let(::detach) ?: uiHelper.detach()
        disposed = true
        active = false
        choreographer.removeFrameCallback(this)
        scope.cancel()
        loadJob = null
        onPartTap = null
        uiHelper.renderCallback = null
        val e = engine ?: return
        scenery?.dispose()
        scenery = null
        resourceLoader?.asyncCancelLoad()
        asset?.let { scene?.removeEntities(it.entities); assetLoader?.destroyAsset(it) }
        asset = null
        resourceLoader?.evictResourceData()
        resourceLoader?.destroy()
        assetLoader?.destroy()
        materials?.destroyMaterials()
        materials?.destroy()
        surfaceMaterials.clear()
        namedRenderables.clear()
        nativeLampEntities.clear()
        lightEntities.forEach { scene?.removeEntity(it); e.destroyEntity(it); EntityManager.get().destroy(it) }
        scene?.indirectLight = null
        indirect?.let(e::destroyIndirectLight)
        reflections?.let(e::destroyTexture)
        view?.let(e::destroyView)
        scene?.let(e::destroyScene)
        renderer?.let(e::destroyRenderer)
        if (cameraEntity != 0) { e.destroyCameraComponent(cameraEntity); EntityManager.get().destroy(cameraEntity) }
        e.destroy()
        engine = null
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Vehicle renderer must be accessed on the main thread" }
    }

    private data class PreparedScene(val model: ByteBuffer, val environment: ByteBuffer,
        val definitions: Map<String,List<SurfaceDefaults>>, val scenery: VehicleScenery.Data)
    private data class SurfaceDefaults(val rgba: FloatArray, val emission: FloatArray, val opaque: Boolean, val painted: Boolean)
    private data class SurfaceMaterial(val material: MaterialInstance, val original: SurfaceDefaults,
        val battery: Boolean, val indicator: Boolean, val emissionGain: Float)

    private class MovingPart(val instance: Int, val rest: FloatArray, var fraction: Float = 0f, val current: FloatArray = FloatArray(16))
}
