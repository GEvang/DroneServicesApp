package com.example.droneservicesapp.ui.pointcloud

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.AttributeSet
import android.view.MotionEvent
import com.example.droneservicesapp.data.pointcloud.PointCloudData
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class PointCloudMissionOverlay(
    val vertices: FloatArray,
    val colors: FloatArray,
    val lineVertexCount: Int,
    val pointVertices: FloatArray = FloatArray(0),
    val pointColors: FloatArray = FloatArray(0),
    val pointVertexCount: Int = 0
)

class PointCloudGlView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private val pointRenderer = PointCloudRenderer()
    private var previousX = 0f
    private var previousY = 0f
    private var previousDistance = 0f
    private var previousCenterX = 0f
    private var previousCenterY = 0f

    init {
        setEGLContextClientVersion(2)
        setRenderer(pointRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setPointCloud(pointCloud: PointCloudData) {
        queueEvent {
            pointRenderer.setPointCloud(pointCloud)
        }
    }

    fun setMissionOverlay(overlay: PointCloudMissionOverlay?) {
        queueEvent {
            pointRenderer.setMissionOverlay(overlay)
        }
    }

    fun setPointSize(pointSize: Float) {
        queueEvent {
            pointRenderer.pointSize = pointSize
        }
    }

    fun setHeightColorModeEnabled(enabled: Boolean) {
        queueEvent {
            pointRenderer.setHeightColorModeEnabled(enabled)
        }
    }

    fun resetCamera() {
        queueEvent {
            pointRenderer.resetCamera()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                previousX = event.x
                previousY = event.y
                previousDistance = 0f
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    previousDistance = pointerDistance(event)
                    previousCenterX = pointerCenterX(event)
                    previousCenterY = pointerCenterY(event)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val distance = pointerDistance(event)
                    if (previousDistance > 0f) {
                        val scale = (previousDistance / distance.coerceAtLeast(1f))
                            .coerceIn(MIN_PINCH_SCALE, MAX_PINCH_SCALE)
                        queueEvent { pointRenderer.zoom(scale) }
                    }
                    val centerX = pointerCenterX(event)
                    val centerY = pointerCenterY(event)
                    val dx = centerX - previousCenterX
                    val dy = centerY - previousCenterY
                    if (abs(dx) > TOUCH_EPSILON || abs(dy) > TOUCH_EPSILON) {
                        queueEvent { pointRenderer.pan(dx, dy) }
                    }
                    previousDistance = distance
                    previousCenterX = centerX
                    previousCenterY = centerY
                } else {
                    val dx = event.x - previousX
                    val dy = event.y - previousY
                    queueEvent { pointRenderer.rotate(dx, dy) }
                    previousX = event.x
                    previousY = event.y
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                previousDistance = 0f
                val remainingIndex = if (event.actionIndex == 0) 1 else 0
                if (remainingIndex < event.pointerCount) {
                    previousX = event.getX(remainingIndex)
                    previousY = event.getY(remainingIndex)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                previousDistance = 0f
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun pointerDistance(event: MotionEvent): Float {
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun pointerCenterX(event: MotionEvent): Float = (event.getX(0) + event.getX(1)) / 2f

    private fun pointerCenterY(event: MotionEvent): Float = (event.getY(0) + event.getY(1)) / 2f

    companion object {
        private const val MIN_PINCH_SCALE = 0.92f
        private const val MAX_PINCH_SCALE = 1.08f
        private const val TOUCH_EPSILON = 0.5f
    }
}

private class PointCloudRenderer : GLSurfaceView.Renderer {

    var pointSize: Float = 2.5f

    private var program = 0
    private var positionHandle = 0
    private var colorHandle = 0
    private var matrixHandle = 0
    private var pointSizeHandle = 0
    private var positionBuffer: FloatBuffer? = null
    private var colorBuffer: FloatBuffer? = null
    private var sourceColorBuffer: FloatBuffer? = null
    private var heightColorBuffer: FloatBuffer? = null
    private var overlayPositionBuffer: FloatBuffer? = null
    private var overlayColorBuffer: FloatBuffer? = null
    private var overlayPointPositionBuffer: FloatBuffer? = null
    private var overlayPointColorBuffer: FloatBuffer? = null
    private var pointCount = 0
    private var overlayLineVertexCount = 0
    private var overlayPointVertexCount = 0
    private var cloudSpan = 100f
    private var heightColorModeEnabled = false
    private var viewportWidth = 1
    private var viewportHeight = 1
    private var yaw = 0f
    private var pitch = 45f
    private var distance = 120f
    private var targetX = 0f
    private var targetY = 0f
    private var targetZ = 0f

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val viewProjectionMatrix = FloatArray(16)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.04f, 0.05f, 0.06f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        colorHandle = GLES20.glGetAttribLocation(program, "a_Color")
        matrixHandle = GLES20.glGetUniformLocation(program, "u_MvpMatrix")
        pointSizeHandle = GLES20.glGetUniformLocation(program, "u_PointSize")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        val ratio = viewportWidth.toFloat() / viewportHeight.toFloat()
        Matrix.perspectiveM(projectionMatrix, 0, 55f, ratio, 0.1f, 10000f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (pointCount == 0 || program == 0) return

        updateViewMatrix()
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(matrixHandle, 1, false, viewProjectionMatrix, 0)
        GLES20.glUniform1f(pointSizeHandle, pointSize)

        positionBuffer?.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, positionBuffer)

        colorBuffer?.position(0)
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(colorHandle, 3, GLES20.GL_FLOAT, false, 0, colorBuffer)

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, pointCount)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)

        drawMissionOverlay()
    }

    fun setPointCloud(pointCloud: PointCloudData) {
        positionBuffer = pointCloud.positions.toFloatBuffer()
        sourceColorBuffer = pointCloud.colors.toFloatBuffer()
        heightColorBuffer = createHeightColors(pointCloud.positions, pointCloud.displayedPointCount).toFloatBuffer()
        colorBuffer = if (heightColorModeEnabled) heightColorBuffer else sourceColorBuffer
        pointCount = pointCloud.displayedPointCount
        cloudSpan = pointCloud.bounds.maxSpan.coerceAtLeast(10f)
        resetCamera()
    }

    fun setHeightColorModeEnabled(enabled: Boolean) {
        heightColorModeEnabled = enabled
        colorBuffer = if (enabled) heightColorBuffer ?: sourceColorBuffer else sourceColorBuffer
    }

    fun setMissionOverlay(overlay: PointCloudMissionOverlay?) {
        if (overlay == null || (overlay.lineVertexCount == 0 && overlay.pointVertexCount == 0)) {
            overlayPositionBuffer = null
            overlayColorBuffer = null
            overlayPointPositionBuffer = null
            overlayPointColorBuffer = null
            overlayLineVertexCount = 0
            overlayPointVertexCount = 0
            return
        }
        overlayPositionBuffer = overlay.vertices.takeIf { overlay.lineVertexCount > 0 }?.toFloatBuffer()
        overlayColorBuffer = overlay.colors.takeIf { overlay.lineVertexCount > 0 }?.toFloatBuffer()
        overlayPointPositionBuffer = overlay.pointVertices.takeIf { overlay.pointVertexCount > 0 }?.toFloatBuffer()
        overlayPointColorBuffer = overlay.pointColors.takeIf { overlay.pointVertexCount > 0 }?.toFloatBuffer()
        overlayLineVertexCount = overlay.lineVertexCount
        overlayPointVertexCount = overlay.pointVertexCount
    }

    private fun drawMissionOverlay() {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        if (overlayLineVertexCount > 0) {
            val overlayPositions = overlayPositionBuffer
            val overlayColors = overlayColorBuffer
            if (overlayPositions != null && overlayColors != null) {
                GLES20.glLineWidth(MISSION_OVERLAY_LINE_WIDTH)
                GLES20.glUniform1f(pointSizeHandle, pointSize)

                overlayPositions.position(0)
                GLES20.glEnableVertexAttribArray(positionHandle)
                GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, overlayPositions)

                overlayColors.position(0)
                GLES20.glEnableVertexAttribArray(colorHandle)
                GLES20.glVertexAttribPointer(colorHandle, 3, GLES20.GL_FLOAT, false, 0, overlayColors)

                GLES20.glDrawArrays(GLES20.GL_LINES, 0, overlayLineVertexCount)
            }
        }

        if (overlayPointVertexCount > 0) {
            val pointPositions = overlayPointPositionBuffer
            val pointColors = overlayPointColorBuffer
            if (pointPositions != null && pointColors != null) {
                GLES20.glUniform1f(pointSizeHandle, MISSION_OVERLAY_POINT_SIZE)

                pointPositions.position(0)
                GLES20.glEnableVertexAttribArray(positionHandle)
                GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, pointPositions)

                pointColors.position(0)
                GLES20.glEnableVertexAttribArray(colorHandle)
                GLES20.glVertexAttribPointer(colorHandle, 3, GLES20.GL_FLOAT, false, 0, pointColors)

                GLES20.glDrawArrays(GLES20.GL_POINTS, 0, overlayPointVertexCount)
            }
        }

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    fun resetCamera() {
        yaw = 0f
        pitch = 55f
        distance = cloudSpan * 1.6f
        targetX = 0f
        targetY = 0f
        targetZ = 0f
    }

    fun rotate(dx: Float, dy: Float) {
        yaw -= dx * 0.35f
        pitch = (pitch + dy * 0.25f).coerceIn(-85f, 85f)
    }

    fun zoom(scale: Float) {
        distance = (distance * scale).coerceIn(cloudSpan * 0.05f, cloudSpan * 20f)
    }

    fun pan(dx: Float, dy: Float) {
        val panScale = distance / max(viewportWidth, viewportHeight).toFloat()
        val yawRad = Math.toRadians(yaw.toDouble())
        val rightX = cos(yawRad).toFloat()
        val rightY = -sin(yawRad).toFloat()
        val upZ = 1f
        targetX -= dx * panScale * rightX
        targetY -= dx * panScale * rightY
        targetZ += dy * panScale * upZ
    }

    private fun updateViewMatrix() {
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        val cosPitch = cos(pitchRad).toFloat()
        val eyeX = targetX + distance * sin(yawRad).toFloat() * cosPitch
        val eyeY = targetY - distance * cos(yawRad).toFloat() * cosPitch
        val eyeZ = targetZ + distance * sin(pitchRad).toFloat()
        Matrix.setLookAtM(
            viewMatrix,
            0,
            eyeX,
            eyeY,
            eyeZ,
            targetX,
            targetY,
            targetZ,
            0f,
            0f,
            1f
        )
        Matrix.multiplyMM(viewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
    }

    private fun FloatArray.toFloatBuffer(): FloatBuffer =
        ByteBuffer.allocateDirect(size * BYTES_PER_FLOAT)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(this)
            .apply { position(0) }

    private fun createHeightColors(positions: FloatArray, count: Int): FloatArray {
        if (count <= 0) return FloatArray(0)
        var minZ = Float.POSITIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        repeat(count) { index ->
            val z = positions[index * VALUES_PER_POINT + 2]
            if (z < minZ) minZ = z
            if (z > maxZ) maxZ = z
        }
        val range = (maxZ - minZ).coerceAtLeast(0.1f)
        return FloatArray(count * VALUES_PER_POINT).also { colors ->
            repeat(count) { index ->
                val offset = index * VALUES_PER_POINT
                val t = ((positions[offset + 2] - minZ) / range).coerceIn(0f, 1f)
                colors[offset] = t
                colors[offset + 1] = 1f - kotlin.math.abs(t - 0.5f)
                colors[offset + 2] = 1f - t
            }
        }
    }

    private fun createProgram(vertexShaderSource: String, fragmentShaderSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSource)
        return GLES20.glCreateProgram().also { createdProgram ->
            GLES20.glAttachShader(createdProgram, vertexShader)
            GLES20.glAttachShader(createdProgram, fragmentShader)
            GLES20.glLinkProgram(createdProgram)
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
        }
    }

    private fun compileShader(type: Int, source: String): Int =
        GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
        }

    companion object {
        private const val BYTES_PER_FLOAT = 4
        private const val VALUES_PER_POINT = 3
        private const val MISSION_OVERLAY_LINE_WIDTH = 6f
        private const val MISSION_OVERLAY_POINT_SIZE = 14f
        private const val VERTEX_SHADER = """
            uniform mat4 u_MvpMatrix;
            uniform float u_PointSize;
            attribute vec4 a_Position;
            attribute vec3 a_Color;
            varying vec3 v_Color;
            void main() {
                v_Color = a_Color;
                gl_Position = u_MvpMatrix * a_Position;
                gl_PointSize = u_PointSize;
            }
        """
        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec3 v_Color;
            void main() {
                gl_FragColor = vec4(v_Color, 1.0);
            }
        """
    }
}
