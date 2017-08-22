package com.kotato.videoencoder

import android.graphics.SurfaceTexture
import android.opengl.*
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Created by kotato on 2017/08/21.
 */
private val EGL_RECORDABLE_ANDROID = 0x3142

object OutPutSurfaceFactory{
    private val TAG ="OutPutSurfaceFactory"
    private var display: EGLDisplay? = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext? = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface? = EGL14.EGL_NO_SURFACE
    private var textureSurface: SurfaceTexture? = null
    private var textureId: Int = 0

    fun createOutPutSurface(width:Int = 0, height:Int = 0) : Surface {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val textureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE)
        textureSurface = SurfaceTexture(textureId).apply {
            setOnFrameAvailableListener(OnFrameAvailableListener())
        }

        return Surface(textureSurface)
    }

    fun checkGlError(op: String) {
        val error: Int= GLES20.glGetError()
        while ((error ) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, op + ": glError " + error)
            throw RuntimeException(op + ": glError " + error)
        }
    }


    private fun setUpELG(width: Int, height: Int) {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display === EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("unable to get EGL14 display")
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            display = null
            throw RuntimeException("unable to initialize EGL14")
        }
        //画面設定　とりあえず参考と同じに
        val attribList = intArrayOf(EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE)
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attribList, 0, configs, 0, configs.size,
                numConfigs, 0)) {
            throw RuntimeException("unable to find RGB888+recordable ES2 EGL config")
        }
        // Configure context for OpenGL ES 2.0.
        val attributes = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                attributes, 0)
        // Create a pbuffer surface.  By using this for output, we can use glReadPixels
        // to test values in the output.
        val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE)
        surface = EGL14.eglCreatePbufferSurface(display, configs[0], surfaceAttribs, 0)
        if (surface == null) {
            throw RuntimeException("surface was null")
        }
    }



    class OnFrameAvailableListener: SurfaceTexture.OnFrameAvailableListener{
        private val FLOAT_SIZE_BYTES = 4
        private val TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES
        private val TRIANGLE_VERTICES_DATA_POS_OFFSET = 0
        private val TRIANGLE_VERTICES_DATA_UV_OFFSET = 3
        private val mTriangleVerticesData = floatArrayOf(
                // X, Y, Z, U, V
                -1.0f, -1.0f, 0f, 0f, 0f, 1.0f, -1.0f, 0f, 1f, 0f, -1.0f, 1.0f, 0f, 0f, 1f, 1.0f, 1.0f, 0f, 1f, 1f)
        private var mTriangleVertices: FloatBuffer? = null
        private val mMVPMatrix = FloatArray(16)
        private val mSTMatrix = FloatArray(16)
        private var mProgram: Int = 0
        private var muMVPMatrixHandle: Int = 0
        private var muSTMatrixHandle: Int = 0
        private var maPositionHandle: Int = 0
        private var maTextureHandle: Int = 0

        init {
            mTriangleVertices = ByteBuffer.allocateDirect(
                    mTriangleVerticesData.size * FLOAT_SIZE_BYTES)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer()
                    .apply { put(mTriangleVerticesData).position(0) }
            Matrix.setIdentityM(mSTMatrix, 0)
        }

        fun drawFrame(st: SurfaceTexture) {
            st.getTransformMatrix(mSTMatrix)
            GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f)
            GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(mProgram)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            mTriangleVertices?.position(TRIANGLE_VERTICES_DATA_POS_OFFSET)
            GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false,
                    TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices)
            GLES20.glEnableVertexAttribArray(maPositionHandle)
            mTriangleVertices?.position(TRIANGLE_VERTICES_DATA_UV_OFFSET)
            GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false,
                    TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices)
            GLES20.glEnableVertexAttribArray(maTextureHandle)
            Matrix.setIdentityM(mMVPMatrix, 0)
            GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0)
            GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glFinish()
        }

        override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {

        }


    }

}