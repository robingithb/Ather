package com.aether.hmi.feature.vehicle.renderer

import android.content.res.Resources
import android.annotation.SuppressLint
import android.os.Looper
import com.aether.hmi.R
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Scene
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.json.JSONArray
import org.json.JSONObject

/** Static world geometry: the native camera moves the scenery, platform and vehicle together. */
internal class VehicleScenery(private val engine: Engine, private val scene: Scene, materials: UbershaderProvider) {
    data class Data(val glb: ByteBuffer, val panorama: ByteBuffer, val floorPng: ByteBuffer)
    private val assets = AssetLoader(engine, materials, EntityManager.get())
    private val resources = ResourceLoader(engine)
    private var asset: FilamentAsset? = null
    private var source: Data? = null
    private val ready = IntArray(16)
    private var disposed = false
    private var showBatteryGauge = false
    private var batteryFraction: Float? = null

    private var batteryTrackEntity = 0
    private var batteryFillEntity = 0
    private val batteryFillTransform = floatArrayOf(1f,0f,0f,0f, 0f,1f,0f,0f,
        0f,0f,1f,0f, 0f,0f,-.908f,1f)

    fun updateBatteryGauge(charging: Boolean, fraction: Float?) {
        checkMainThread()
        val level = fraction?.coerceIn(0f, 1f)
        if (showBatteryGauge == charging && batteryFraction == level) return
        val visibilityChanged = showBatteryGauge != charging
        showBatteryGauge = charging
        batteryFraction = level
        // The animation only touches one cached fill transform; no scene scan or new mesh.
        if (visibilityChanged) applyBatteryGauge(batteryTrackEntity, false)
        applyBatteryGauge(batteryFillEntity, true)
    }

    private fun configureBatteryGauge(loaded: FilamentAsset, entity: Int) {
        when (loaded.getName(entity)) {
            "AETHER_BATTERY_SOC_TRACK" -> { batteryTrackEntity = entity; applyBatteryGauge(entity, false) }
            "AETHER_BATTERY_SOC_FILL" -> { batteryFillEntity = entity; applyBatteryGauge(entity, true) }
        }
    }

    private fun applyBatteryGauge(entity: Int, fill: Boolean) {
        if (entity == 0) return
        val manager = engine.renderableManager
        val instance = manager.getInstance(entity)
        if (instance == 0) return
        val level = batteryFraction ?: 0f
        manager.setLayerMask(instance, 0xff, if (showBatteryGauge && (!fill || level > 0f)) 1 else 0)
        if (fill) {
            batteryFillTransform[10] = level.coerceAtLeast(.0001f)
            engine.transformManager.setTransform(engine.transformManager.getInstance(entity), batteryFillTransform)
        }
    }

    fun begin(data: Data) {
        checkMainThread()
        check(!disposed && asset == null)
        source = data // Keep direct resource buffers alive through asynchronous upload.
        resources.addResourceData(imageUri, data.panorama)
        resources.addResourceData(floorImageUri, data.floorPng)
        val loaded = requireNotNull(assets.createAsset(data.glb)) { "Invalid generated scenery" }
        asset = loaded
        loaded.renderableEntities.forEach { configureShadows(loaded, it) }
        check(resources.asyncBeginLoad(loaded)) { "Unable to load scenery textures" }
        loaded.releaseSourceData()
    }

    fun update(): Float {
        checkMainThread()
        if (disposed) return 0f
        val loaded = asset ?: return 0f
        resources.asyncUpdateLoad()
        var count = loaded.popRenderables(ready)
        while (count > 0) {
            for (index in 0 until count) configureShadows(loaded, ready[index])
            scene.addEntities(ready.copyOf(count))
            count = loaded.popRenderables(ready)
        }
        return resources.asyncGetLoadProgress().takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    }

    private fun configureShadows(loaded: FilamentAsset, entity: Int) {
        val manager = engine.renderableManager
        val instance = manager.getInstance(entity)
        if (instance == 0) return
        // The panorama and decorative glow must never occlude the model's sun shadow.
        manager.setCastShadows(instance, false)
        manager.setReceiveShadows(instance, loaded.getName(entity) == "AETHER_FLOOR")
        configureBatteryGauge(loaded, entity)
    }

    fun dispose() {
        checkMainThread()
        if (disposed) return
        disposed = true
        resources.asyncCancelLoad()
        asset?.let { scene.removeEntities(it.entities); assets.destroyAsset(it) }
        asset = null
        batteryTrackEntity = 0
        batteryFillEntity = 0
        resources.evictResourceData()
        resources.destroy()
        assets.destroy()
        source = null
        // The shared UbershaderProvider and its materials belong to the renderer.
    }

    private fun checkMainThread() = check(Looper.myLooper() == Looper.getMainLooper())

    companion object {
        private const val imageUri = "aether_scenery.png"
        private const val floorImageUri = "aether_ground.png"
        /** CPU-only preparation; call on Dispatchers.IO before begin() on the native thread. */
        @SuppressLint("ResourceType") // nodpi PNG is an uncompiled binary; read original encoded bytes for glTF.
        fun read(resources: Resources): Data {
            val png = resources.openRawResource(R.drawable.vehicle_stage).use { it.readBytes() }
            val floorPng = resources.openRawResource(R.drawable.vehicle_ground).use { it.readBytes() }
            return Data(direct(buildGlb()), direct(png), direct(floorPng))
        }

        private fun direct(bytes: ByteArray): ByteBuffer = ByteBuffer.allocateDirect(bytes.size)
            .order(ByteOrder.nativeOrder()).apply { put(bytes); flip() }
        private fun obj(vararg values: Pair<String, Any>) = JSONObject().apply { values.forEach { put(it.first, it.second) } }
        private fun array(vararg values: Any) = JSONArray(values.toList())
        private fun vertex(x: Double, y: Double, z: Double, nx: Double = 0.0, ny: Double = 1.0,
            nz: Double = 0.0, u: Double = 0.0, v: Double = 0.0, alpha: Double = 1.0) =
            floatArrayOf(x.toFloat(), y.toFloat(), z.toFloat(), nx.toFloat(), ny.toFloat(), nz.toFloat(),
                u.toFloat(), v.toFloat(), 1f, 1f, 1f, alpha.toFloat())

        private fun buildGlb(): ByteArray {
            val binary = ByteArrayOutputStream()
            val views = JSONArray(); val accessors = JSONArray(); val meshes = JSONArray(); val nodes = JSONArray()
            fun mesh(name: String, vertices: List<FloatArray>, indices: List<Int>, material: Int) {
                val vertexOffset = binary.size()
                val packed = ByteBuffer.allocate(vertices.size * 48).order(ByteOrder.LITTLE_ENDIAN)
                vertices.forEach { value -> value.forEach { packed.putFloat(it) } }
                binary.write(packed.array())
                val vertexView = views.length()
                views.put(obj("buffer" to 0, "byteOffset" to vertexOffset, "byteLength" to packed.capacity(), "byteStride" to 48, "target" to 34962))
                fun attribute(offset: Int, type: String, position: Boolean = false): Int {
                    val value = obj("bufferView" to vertexView, "byteOffset" to offset, "componentType" to 5126,
                        "count" to vertices.size, "type" to type)
                    if (position) {
                        value.put("min", JSONArray((0..2).map { axis -> vertices.minOf { it[axis].toDouble() } }))
                        value.put("max", JSONArray((0..2).map { axis -> vertices.maxOf { it[axis].toDouble() } }))
                    }
                    return accessors.length().also { accessors.put(value) }
                }
                val attributes = obj("POSITION" to attribute(0, "VEC3", true), "NORMAL" to attribute(12, "VEC3"),
                    "TEXCOORD_0" to attribute(24, "VEC2"), "COLOR_0" to attribute(32, "VEC4"))
                val indexOffset = binary.size()
                val indexData = ByteBuffer.allocate(indices.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                indices.forEach { require(it in 0 until vertices.size && it <= 65535); indexData.putShort(it.toShort()) }
                binary.write(indexData.array())
                val indexView = views.length()
                views.put(obj("buffer" to 0, "byteOffset" to indexOffset, "byteLength" to indexData.capacity(), "target" to 34963))
                while (binary.size() % 4 != 0) binary.write(0)
                val indexAccessor = accessors.length()
                accessors.put(obj("bufferView" to indexView, "componentType" to 5123, "count" to indices.size, "type" to "SCALAR"))
                val meshIndex = meshes.length()
                meshes.put(obj("name" to name, "primitives" to array(obj("attributes" to attributes, "indices" to indexAccessor, "material" to material))))
                nodes.put(obj("name" to name, "mesh" to meshIndex))
            }
            val cylinder = mutableListOf<FloatArray>(); val cylinderIndices = mutableListOf<Int>()
            val segments = 96
            for (segment in 0 until segments) {
                val start = cylinder.size
                for (edge in 0..1) {
                    val angle = (segment + edge) * 2.0 * PI / segments
                    val x = cos(angle); val z = sin(angle); val u = (segment + edge) * 2.0 / segments
                    cylinder += vertex(24*x, -2.0, 24*z, -x, 0.0, -z, u, 0.62)
                    cylinder += vertex(24*x, 12.0, 24*z, -x, 0.0, -z, u, 0.0)
                }
                cylinderIndices += listOf(start, start+2, start+1, start+1, start+2, start+3)
            }
            // A dark sky cap avoids revealing a rectangular compositing edge at low pitches.
            val cap = cylinder.size
            cylinder += vertex(0.0, 12.0, 0.0, 0.0, -1.0, 0.0, 0.0, 0.0)
            for (segment in 0 until segments) cylinderIndices += listOf(cap, segment*4+1, segment*4+3)
            mesh("AETHER_PANORAMA", cylinder, cylinderIndices, 0)
            // World-space repeat UVs preserve the stone scale through every camera orbit.
            // An eight-millimetre gap avoids z-fighting at the measured Y=0 tyre contacts.
            fun floorCorner(x: Double, z: Double) = vertex(x, -.008, z, u = x/6.0 + .5, v = z/6.0 + .5)
            mesh("AETHER_FLOOR", listOf(floorCorner(-40.0,-40.0), floorCorner(40.0,-40.0),
                floorCorner(40.0,40.0), floorCorner(-40.0,40.0)), listOf(0,2,1,0,3,2), 1)
            fun annulus(name: String, radii: List<Double>, opacity: List<Double>, y: Double, material: Int) {
                require(radii.size == opacity.size)
                val vertices = mutableListOf<FloatArray>(); val indices = mutableListOf<Int>()
                for (radiusIndex in radii.indices) for (segment in 0..segments) {
                    val angle = segment*2.0*PI/segments
                    val radius = radii[radiusIndex]
                    vertices += vertex(radius*cos(angle), y, radius*sin(angle), alpha = opacity[radiusIndex])
                    if (radiusIndex < radii.lastIndex && segment < segments) {
                        val i = radiusIndex*(segments+1)+segment
                        indices += listOf(i,i+segments+1,i+1,i+1,i+segments+1,i+segments+2)
                    }
                }
                mesh(name, vertices, indices, material)
            }
            annulus("AETHER_PLATFORM_RIM", listOf(2.590,2.710), listOf(1.0,1.0), -.004, 2)
            annulus("AETHER_PLATFORM_GLOW", listOf(2.520,2.585,2.620,2.637,2.663,2.680,2.715,2.780),
                listOf(0.0,.035,.17,.5,.5,.17,.035,0.0), -.002, 3)
            annulus("AETHER_PLATFORM_RING", listOf(2.637,2.663), listOf(1.0,1.0), 0.0, 4)
            annulus("AETHER_PLATFORM_CORE", listOf(2.646,2.654), listOf(1.0,1.0), .0005, 5)
            val shadow = mutableListOf<FloatArray>(); val shadowIndices = mutableListOf<Int>()
            fun footprint(cx: Double, cz: Double, rx: Double, rz: Double, strength: Double) {
                val base = shadow.size
                for (radial in 0..6) for (segment in 0..48) {
                    val radius = radial / 6.0; val angle = segment * 2.0 * PI / 48
                    shadow += vertex(cx+rx*radius*cos(angle), -.006, cz+rz*radius*sin(angle), alpha = strength*(1-radius)*(1-radius))
                    if (radial < 6 && segment < 48) {
                        val i = base + radial*49 + segment
                        shadowIndices += listOf(i,i+49,i+1,i+1,i+49,i+50)
                    }
                }
            }
            footprint(0.0,0.0,1.12,2.15,.20)
            footprint(0.0,-.02,.86,1.72,.40)
            for (x in listOf(-.76,.77)) for (z in listOf(-1.36,1.28)) footprint(x,z,.19,.34,.96)
            mesh("AETHER_CONTACT_SHADOW", shadow, shadowIndices, 6)
            // A single fill quad is anchored to the measured battery top plate, Y=.458.
            // Its Z scale expresses actual SOC; these are UI marks, not invented cells.
            mesh("AETHER_BATTERY_SOC_TRACK", listOf(vertex(-.528,.460,-.908), vertex(.528,.460,-.908),
                vertex(.528,.460,.708), vertex(-.528,.460,.708)), listOf(0,2,1,0,3,2), 7)
            mesh("AETHER_BATTERY_SOC_FILL", listOf(vertex(-.528,.462,0.0), vertex(.528,.462,0.0),
                vertex(.528,.462,1.616), vertex(-.528,.462,1.616)), listOf(0,2,1,0,3,2), 8)
            fun material(name: String, color: JSONArray, unlit: Boolean, blend: Boolean = false) =
                obj("name" to name, "doubleSided" to true, "alphaMode" to if (blend) "BLEND" else "OPAQUE",
                    "pbrMetallicRoughness" to obj("baseColorFactor" to color, "metallicFactor" to .25, "roughnessFactor" to .35))
                    .apply { if (unlit) put("extensions", obj("KHR_materials_unlit" to JSONObject())) }
            val panorama = material("Aether panorama", array(.7,.75,.8,1), true).apply {
                getJSONObject("pbrMetallicRoughness").put("baseColorTexture", obj("index" to 0))
            }
            val floor = material("Aether wet stone", array(.70,.73,.78,1), false).apply {
                getJSONObject("pbrMetallicRoughness").apply {
                    put("baseColorTexture", obj("index" to 1))
                    put("metallicFactor", .15)
                    put("roughnessFactor", .22)
                }
                put("extensions", obj("KHR_materials_clearcoat" to obj("clearcoatFactor" to 1.0,
                    "clearcoatRoughnessFactor" to .10)))
            }
            val rim = material("Aether dark platform rim", array(.025,.040,.065,1), false).apply {
                getJSONObject("pbrMetallicRoughness").put("metallicFactor", .65).put("roughnessFactor", .18)
            }
            val document = obj("asset" to obj("version" to "2.0", "generator" to "AETHER native scenery"), "scene" to 0,
                "scenes" to array(obj("nodes" to JSONArray((0 until nodes.length()).toList()))), "nodes" to nodes, "meshes" to meshes,
                "materials" to array(panorama, floor, rim,
                    material("Aether ring halo", array(.025,.35,1.0,1), true, true),
                    material("Aether cyan ring", array(.15,.67,1.0,1), true),
                    material("Aether ring white core", array(.65,.92,1.0,1), true),
                    material("Aether soft contact", array(0,0,0,1), true, true),
                    material("Aether battery level track", array(.035,.045,.050,1), true),
                    material("Aether battery level fill", array(.045,.68,.19,1), true)),
                "extensionsUsed" to array("KHR_materials_unlit", "KHR_materials_clearcoat"),
                "images" to array(obj("uri" to imageUri, "mimeType" to "image/png"),
                    obj("uri" to floorImageUri, "mimeType" to "image/png")),
                "samplers" to array(obj("magFilter" to 9729, "minFilter" to 9987, "wrapS" to 33648, "wrapT" to 33071),
                    obj("magFilter" to 9729, "minFilter" to 9987, "wrapS" to 10497, "wrapT" to 10497)),
                "textures" to array(obj("sampler" to 0, "source" to 0), obj("sampler" to 1, "source" to 1)),
                "bufferViews" to views, "accessors" to accessors,
                "buffers" to array(obj("byteLength" to binary.size())))
            // Android JSONObject escapes '/', but cgltf's MIME dispatch expects literal "image/png".
            val json = document.toString().replace("\\/", "/").toByteArray(Charsets.UTF_8)
            val jsonSize = (json.size + 3) and -4
            return ByteBuffer.allocate(12+8+jsonSize+8+binary.size()).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(0x46546C67); putInt(2); putInt(capacity()); putInt(jsonSize); putInt(0x4E4F534A); put(json)
                repeat(jsonSize-json.size) { put(32.toByte()) }; putInt(binary.size()); putInt(0x004E4942); put(binary.toByteArray())
            }.array()
        }
    }
}
