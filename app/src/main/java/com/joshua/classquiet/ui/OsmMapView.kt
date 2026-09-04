package com.joshua.classquiet.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.LruCache
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

/**
 * A small native slippy-map view for selecting class coordinates.
 *
 * It intentionally has no JavaScript or CDN dependency. Only the visible OpenStreetMap raster
 * tiles are requested, and the selected point plus radius are rendered by Android Canvas.
 */
internal class OsmMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    var onLocationSelected: ((Double, Double) -> Unit)? = null

    private var centerLatitude = DEFAULT_LATITUDE
    private var centerLongitude = DEFAULT_LONGITUDE
    private var selectedLatitude: Double? = null
    private var selectedLongitude: Double? = null
    private var radiusMeters = 150f
    private var zoom = DEFAULT_ZOOM
    private var accumulatedScale = 1f

    private val mainHandler = Handler(Looper.getMainLooper())
    private val tileExecutor = Executors.newFixedThreadPool(3)
    private val tileCacheDirectory = File(context.applicationContext.filesDir, "osm_tile_cache")
    private val pendingTiles = ConcurrentHashMap.newKeySet<String>()
    private val tileRetryAfter = ConcurrentHashMap<String, Long>()
    private val failedTileRequests = AtomicInteger(0)
    private var released = false

    private val tileCache = object : LruCache<String, Bitmap>(TILE_CACHE_KILOBYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(229, 233, 242)
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(207, 214, 228)
        style = Paint.Style.STROKE
        strokeWidth = density(1f)
    }
    private val radiusFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(48, 53, 89, 199)
        style = Paint.Style.FILL
    }
    private val radiusStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(53, 89, 199)
        style = Paint.Style.STROKE
        strokeWidth = density(2f)
    }
    private val pinFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(53, 89, 199)
        style = Paint.Style.FILL
    }
    private val pinOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = density(3f)
        strokeJoin = Paint.Join.ROUND
    }
    private val messagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(55, 61, 76)
        textAlign = Paint.Align.CENTER
        textSize = density(14f)
    }
    private val messageBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(224, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val attributionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(48, 52, 61)
        textAlign = Paint.Align.RIGHT
        textSize = density(10f)
    }
    private val attributionBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(218, 255, 255, 255)
        style = Paint.Style.FILL
    }

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onScroll(
                first: MotionEvent?,
                current: MotionEvent,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                if (first == null || current.pointerCount > 1) return false
                panBy(distanceX.toDouble(), distanceY.toDouble())
                return true
            }

            override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                selectAtScreenPoint(event.x.toDouble(), event.y.toDouble())
                return true
            }

            override fun onDoubleTap(event: MotionEvent): Boolean {
                zoomIn()
                return true
            }
        },
    )

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                accumulatedScale *= detector.scaleFactor
                when {
                    accumulatedScale >= 1.25f -> {
                        zoomIn()
                        accumulatedScale = 1f
                    }
                    accumulatedScale <= 0.8f -> {
                        zoomOut()
                        accumulatedScale = 1f
                    }
                }
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                accumulatedScale = 1f
            }
        },
    )

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "OpenStreetMap location picker. Tap to place a pin."
        tileExecutor.execute { pruneDiskCache() }
    }

    fun setInitialLocation(latitude: Double?, longitude: Double?) {
        if (latitude != null && longitude != null && validCoordinates(latitude, longitude)) {
            centerLatitude = latitude
            centerLongitude = longitude
            selectedLatitude = latitude
            selectedLongitude = longitude
        } else {
            centerLatitude = DEFAULT_LATITUDE
            centerLongitude = DEFAULT_LONGITUDE
            selectedLatitude = null
            selectedLongitude = null
        }
        invalidate()
    }

    fun setRadiusMeters(value: Float) {
        radiusMeters = value.coerceIn(25f, 5_000f)
        invalidate()
    }

    fun zoomIn() = changeZoom(zoom + 1)

    fun zoomOut() = changeZoom(zoom - 1)

    fun release() {
        if (released) return
        released = true
        onLocationSelected = null
        tileExecutor.shutdownNow()
        pendingTiles.clear()
        tileRetryAfter.clear()
        tileCache.evictAll()
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                parent?.requestDisallowInterceptTouchEvent(false)
        }
        val scaled = scaleDetector.onTouchEvent(event)
        val gestured = gestureDetector.onTouchEvent(event)
        return scaled || gestured || super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(232, 236, 245))
        if (width <= 0 || height <= 0) return

        val worldSize = worldSize()
        val center = latitudeLongitudeToWorld(centerLatitude, centerLongitude, worldSize)
        val topLeftX = center.x - width / 2.0
        val topLeftY = center.y - height / 2.0
        val firstTileX = floor(topLeftX / TILE_SIZE).toInt()
        val lastTileX = floor((topLeftX + width) / TILE_SIZE).toInt()
        val firstTileY = max(0, floor(topLeftY / TILE_SIZE).toInt())
        val tileCount = 1 shl zoom
        val lastTileY = min(tileCount - 1, floor((topLeftY + height) / TILE_SIZE).toInt())
        var visibleLoadedTiles = 0

        for (tileY in firstTileY..lastTileY) {
            for (unwrappedTileX in firstTileX..lastTileX) {
                val tileX = floorMod(unwrappedTileX, tileCount)
                val key = "$zoom/$tileX/$tileY"
                val left = (unwrappedTileX * TILE_SIZE - topLeftX).toFloat()
                val top = (tileY * TILE_SIZE - topLeftY).toFloat()
                val destination = RectF(
                    left,
                    top,
                    left + TILE_SIZE.toFloat() + 0.5f,
                    top + TILE_SIZE.toFloat() + 0.5f,
                )
                val bitmap = tileCache.get(key)
                if (bitmap == null) {
                    canvas.drawRect(destination, placeholderPaint)
                    canvas.drawRect(destination, gridPaint)
                    requestTile(key, zoom, tileX, tileY)
                } else {
                    canvas.drawBitmap(bitmap, null, destination, tilePaint)
                    visibleLoadedTiles += 1
                }
            }
        }

        drawSelection(canvas, center, worldSize)
        drawStatus(canvas, visibleLoadedTiles)
        drawAttribution(canvas)
    }

    private fun requestTile(key: String, requestedZoom: Int, tileX: Int, tileY: Int) {
        val now = System.currentTimeMillis()
        if (released || (tileRetryAfter[key] ?: 0L) > now || !pendingTiles.add(key)) return
        runCatching { tileExecutor.execute {
            val bitmap = runCatching {
                val cacheFile = tileFile(requestedZoom, tileX, tileY)
                val cacheIsFresh =
                    cacheFile.isFile &&
                    System.currentTimeMillis() - cacheFile.lastModified() < TILE_CACHE_MAX_AGE_MILLIS
                val cachedBitmap = if (cacheIsFresh) {
                    BitmapFactory.decodeFile(cacheFile.absolutePath)
                } else {
                    null
                }
                if (cachedBitmap != null) {
                    cachedBitmap
                } else {
                    if (cacheIsFresh) cacheFile.delete()
                    val connection = URL(
                        "https://tile.openstreetmap.org/$requestedZoom/$tileX/$tileY.png",
                    ).openConnection() as HttpURLConnection
                    try {
                        connection.connectTimeout = TILE_TIMEOUT_MILLIS
                        connection.readTimeout = TILE_TIMEOUT_MILLIS
                        connection.instanceFollowRedirects = true
                        connection.setRequestProperty(
                            "User-Agent",
                            "QuietClasses/1.2.2 (+https://classquiet.nafzigers.us)",
                        )
                        connection.setRequestProperty("Referer", "https://classquiet.nafzigers.us/")
                        if (cacheFile.isFile) connection.ifModifiedSince = cacheFile.lastModified()
                        when (connection.responseCode) {
                            HttpURLConnection.HTTP_NOT_MODIFIED -> {
                                cacheFile.setLastModified(System.currentTimeMillis())
                                BitmapFactory.decodeFile(cacheFile.absolutePath)
                            }
                            HttpURLConnection.HTTP_OK -> {
                                val bytes = connection.inputStream.use { stream -> stream.readBytes() }
                                val downloaded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                if (downloaded != null) {
                                    cacheFile.parentFile?.mkdirs()
                                    cacheFile.outputStream().use { stream -> stream.write(bytes) }
                                }
                                downloaded
                            }
                            else -> error("Tile server returned ${connection.responseCode}")
                        }
                    } finally {
                        connection.disconnect()
                    }
                }
            }.getOrNull()

            mainHandler.post {
                pendingTiles.remove(key)
                if (released) return@post
                if (bitmap != null) {
                    tileRetryAfter.remove(key)
                    tileCache.put(key, bitmap)
                } else {
                    tileRetryAfter[key] = System.currentTimeMillis() + TILE_RETRY_DELAY_MILLIS
                    failedTileRequests.incrementAndGet()
                    postInvalidateDelayed(TILE_RETRY_DELAY_MILLIS)
                }
                invalidate()
            }
        } }.onFailure {
            pendingTiles.remove(key)
        }
    }

    private fun tileFile(requestedZoom: Int, tileX: Int, tileY: Int): File =
        File(tileCacheDirectory, "$requestedZoom/$tileX/$tileY.png")

    private fun pruneDiskCache() {
        runCatching {
            if (!tileCacheDirectory.isDirectory) return@runCatching
            val files = tileCacheDirectory.walkTopDown().filter(File::isFile).toList()
            var totalBytes = files.sumOf(File::length)
            if (totalBytes <= MAX_DISK_CACHE_BYTES) return@runCatching
            val now = System.currentTimeMillis()
            files.asSequence()
                .filter { now - it.lastModified() >= TILE_CACHE_MAX_AGE_MILLIS }
                .sortedBy(File::lastModified)
                .forEach { file ->
                if (totalBytes <= MAX_DISK_CACHE_BYTES) return@forEach
                val length = file.length()
                if (file.delete()) totalBytes -= length
            }
        }
    }

    private fun drawSelection(canvas: Canvas, center: WorldPoint, worldSize: Double) {
        val latitude = selectedLatitude
        val longitude = selectedLongitude
        if (latitude == null || longitude == null) return

        val point = latitudeLongitudeToWorld(latitude, longitude, worldSize)
        var deltaX = point.x - center.x
        if (deltaX > worldSize / 2.0) deltaX -= worldSize
        if (deltaX < -worldSize / 2.0) deltaX += worldSize
        val screenX = (width / 2.0 + deltaX).toFloat()
        val screenY = (height / 2.0 + point.y - center.y).toFloat()
        val metersPerPixel = (
            cos(Math.toRadians(latitude)) * 2.0 * PI * EARTH_RADIUS_METERS / worldSize
        ).coerceAtLeast(0.01)
        val radiusPixels = (radiusMeters / metersPerPixel).toFloat()

        canvas.drawCircle(screenX, screenY, radiusPixels, radiusFillPaint)
        canvas.drawCircle(screenX, screenY, radiusPixels, radiusStrokePaint)
        drawPin(canvas, screenX, screenY)
    }

    private fun drawPin(canvas: Canvas, anchorX: Float, anchorY: Float) {
        val pinHeight = density(42f)
        val pinRadius = density(15f)
        val centerY = anchorY - pinHeight + pinRadius
        val path = Path().apply {
            moveTo(anchorX, anchorY)
            cubicTo(
                anchorX - density(4f),
                anchorY - density(8f),
                anchorX - pinRadius,
                centerY + density(8f),
                anchorX - pinRadius,
                centerY,
            )
            cubicTo(
                anchorX - pinRadius,
                centerY - pinRadius,
                anchorX + pinRadius,
                centerY - pinRadius,
                anchorX + pinRadius,
                centerY,
            )
            cubicTo(
                anchorX + pinRadius,
                centerY + density(8f),
                anchorX + density(4f),
                anchorY - density(8f),
                anchorX,
                anchorY,
            )
            close()
        }
        canvas.drawPath(path, pinOutlinePaint)
        canvas.drawPath(path, pinFillPaint)
        canvas.drawCircle(anchorX, centerY, density(5f), messageBackgroundPaint)
    }

    private fun drawStatus(canvas: Canvas, visibleLoadedTiles: Int) {
        val message = when {
            visibleLoadedTiles == 0 && failedTileRequests.get() > 0 ->
                "Map tiles unavailable — you can still tap to place the pin"
            visibleLoadedTiles == 0 -> "Loading map tiles…"
            selectedLatitude == null -> "Tap the map to place the pin"
            else -> null
        } ?: return

        val centerX = width / 2f
        val baseline = density(29f)
        val textWidth = messagePaint.measureText(message)
        val rect = RectF(
            centerX - textWidth / 2f - density(12f),
            density(8f),
            centerX + textWidth / 2f + density(12f),
            density(40f),
        )
        canvas.drawRoundRect(rect, density(18f), density(18f), messageBackgroundPaint)
        canvas.drawText(message, centerX, baseline, messagePaint)
    }

    private fun drawAttribution(canvas: Canvas) {
        val label = "© OpenStreetMap contributors"
        val right = width - density(5f)
        val baseline = height - density(5f)
        val textWidth = attributionPaint.measureText(label)
        canvas.drawRect(
            right - textWidth - density(7f),
            baseline - density(13f),
            right + density(3f),
            baseline + density(3f),
            attributionBackgroundPaint,
        )
        canvas.drawText(label, right, baseline, attributionPaint)
    }

    private fun selectAtScreenPoint(screenX: Double, screenY: Double) {
        val worldSize = worldSize()
        val center = latitudeLongitudeToWorld(centerLatitude, centerLongitude, worldSize)
        val point = WorldPoint(
            x = center.x + screenX - width / 2.0,
            y = center.y + screenY - height / 2.0,
        )
        val coordinates = worldToLatitudeLongitude(point, worldSize)
        selectedLatitude = coordinates.latitude
        selectedLongitude = coordinates.longitude
        onLocationSelected?.invoke(coordinates.latitude, coordinates.longitude)
        performClick()
        invalidate()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun panBy(distanceX: Double, distanceY: Double) {
        val worldSize = worldSize()
        val center = latitudeLongitudeToWorld(centerLatitude, centerLongitude, worldSize)
        val moved = WorldPoint(
            x = center.x + distanceX,
            y = (center.y + distanceY).coerceIn(0.0, worldSize),
        )
        val coordinates = worldToLatitudeLongitude(moved, worldSize)
        centerLatitude = coordinates.latitude
        centerLongitude = coordinates.longitude
        invalidate()
    }

    private fun changeZoom(newZoom: Int) {
        val clamped = newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (clamped == zoom) return
        zoom = clamped
        invalidate()
    }

    private fun worldSize(): Double = TILE_SIZE * 2.0.pow(zoom)

    private fun latitudeLongitudeToWorld(
        latitude: Double,
        longitude: Double,
        worldSize: Double,
    ): WorldPoint {
        val clampedLatitude = latitude.coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE)
        val latitudeRadians = Math.toRadians(clampedLatitude)
        return WorldPoint(
            x = (longitude + 180.0) / 360.0 * worldSize,
            y = (
                1.0 - ln(tan(latitudeRadians) + 1.0 / cos(latitudeRadians)) / PI
            ) / 2.0 * worldSize,
        )
    }

    private fun worldToLatitudeLongitude(point: WorldPoint, worldSize: Double): Coordinates {
        val wrappedX = ((point.x % worldSize) + worldSize) % worldSize
        val clampedY = point.y.coerceIn(0.0, worldSize)
        val longitude = wrappedX / worldSize * 360.0 - 180.0
        val latitude = Math.toDegrees(atan(sinh(PI - 2.0 * PI * clampedY / worldSize)))
        return Coordinates(latitude, longitude)
    }

    private fun floorMod(value: Int, modulus: Int): Int = ((value % modulus) + modulus) % modulus

    private fun validCoordinates(latitude: Double, longitude: Double): Boolean =
        latitude in -90.0..90.0 && longitude in -180.0..180.0

    private fun density(value: Float): Float = value * resources.displayMetrics.density

    private data class WorldPoint(val x: Double, val y: Double)

    private data class Coordinates(val latitude: Double, val longitude: Double)

    private companion object {
        const val DEFAULT_LATITUDE = 37.2284
        const val DEFAULT_LONGITUDE = -80.4234
        const val DEFAULT_ZOOM = 15
        const val MIN_ZOOM = 3
        const val MAX_ZOOM = 19
        const val TILE_SIZE = 256.0
        const val TILE_CACHE_KILOBYTES = 16 * 1024
        const val TILE_TIMEOUT_MILLIS = 10_000
        const val TILE_RETRY_DELAY_MILLIS = 30_000L
        const val TILE_CACHE_MAX_AGE_MILLIS = 7L * 24L * 60L * 60L * 1_000L
        const val MAX_DISK_CACHE_BYTES = 64L * 1024L * 1024L
        const val MAX_MERCATOR_LATITUDE = 85.05112878
        const val EARTH_RADIUS_METERS = 6_378_137.0
    }
}
