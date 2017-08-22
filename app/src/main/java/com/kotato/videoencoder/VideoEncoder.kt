package com.kotato.videoencoder

import android.media.*
import android.opengl.*
import android.util.Log
import android.view.Surface
import net.ypresto.androidtranscoder.engine.QueuedMuxer
import net.ypresto.androidtranscoder.engine.VideoTrackTranscoder
import net.ypresto.androidtranscoder.format.MediaFormatExtraConstants
import net.ypresto.androidtranscoder.format.MediaFormatStrategyPresets
import net.ypresto.androidtranscoder.format.OutputFormatUnavailableException
import net.ypresto.androidtranscoder.utils.MediaExtractorUtils
import java.io.File
import java.io.IOException
import java.nio.Buffer
import java.nio.ByteBuffer

/**
 * Created by kotato on 2017/08/11.
 */

val TIMEOUT_USEC = 10000L

class VideoEncoder(val file: File, val outputPath: String){
    private val TAG = "VideoEncoder"
    var extractor: MediaExtractor = MediaExtractor()
    var muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

    var videoEncoder:MediaCodec? = null
    var videoDecoder:MediaCodec? = null
    var videoOutputBuffer:ByteBuffer? = null
    var videoInputBuffer:ByteBuffer? = null

    lateinit var quedMuxer : QueuedMuxer
    val bufferInfo = MediaCodec.BufferInfo()

    val strategy = MediaFormatStrategyPresets.createAndroid720pStrategy(8000 * 1000, 128 * 1000, 1)
    var mDurationUs = 0L
    fun main(){

        extractor.setDataSource(file.inputStream().fd)
        setupMetadata()
        quedMuxer = QueuedMuxer(muxer, QueuedMuxer.Listener {
        })
        val videoFormat = getTrack("video/")
        videoFormat?.apply {
            val trackResult = MediaExtractorUtils.getFirstVideoAndAudioTrack(extractor)
            println(trackResult.mVideoTrackFormat)
            val mVideoTrackTranscoder = VideoTrackTranscoder(extractor, trackResult.mVideoTrackIndex
                    , strategy.createVideoOutputFormat(trackResult.mVideoTrackFormat), quedMuxer).apply {
                setup()
                extractor.selectTrack(first)
            }
            var loopCount: Long = 0
            while (!(mVideoTrackTranscoder.isFinished())) {
                val stepped = mVideoTrackTranscoder.stepPipeline()
                loopCount++
                if (!stepped) {
                    try {
                        Thread.sleep(1000)
                    } catch (e: InterruptedException) {
                        // nothing to do
                    }

                }
            }

//            extractor.selectTrack(first)
//            videoEncoder = MediaCodec.createEncoderByType(second.getString(MediaFormat.KEY_MIME)).apply {
//                val format = createVideoFormat(second, 800 * 1000)
//                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
//                val inputSurface  = createInputSurface()
//                EGLParam(inputSurface).apply {
//                    if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
//                        throw RuntimeException("eglMakeCurrent failed")
//                    }
//                }
//                start()
//            }
//
//            //やらないとLolipo以前で画面が反転するらしい
//            val inputFormat = extractor.getTrackFormat(first)
//            if (inputFormat.containsKey(MediaFormatExtraConstants.KEY_ROTATION_DEGREES)) {
//                inputFormat.setInteger(MediaFormatExtraConstants.KEY_ROTATION_DEGREES, 0)
//            }
//
//            videoDecoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME))
//                    .apply {
//                        configure(inputFormat, OutputSurface().surface, null, 0)
//                        start()
//                    }
//            extractor.selectTrack(first)
        }
//
//        val audioFormat = getTrack("audio/")
//        audioFormat?.apply {
//            createAudioFormat(second, 8000 * 1000)
//            extractor.selectTrack(first)
//        }
        println(videoDecoder?.outputBuffers?.size.toString())
        loop@ while (true){
            if(drainEncoder(0) == 111) return@loop
        }

        println("end!!")

    }


    @Throws(IOException::class)
    private fun setupMetadata() {
        val mediaMetadataRetriever = MediaMetadataRetriever()
        mediaMetadataRetriever.setDataSource(file.inputStream().fd)

        val rotationString = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
        try {
            muxer.setOrientationHint(Integer.parseInt(rotationString))
        } catch (e: NumberFormatException) {
            // skip
        }

        try {
            mDurationUs = java.lang.Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) * 1000
        } catch (e: NumberFormatException) {
            mDurationUs = -1
        }

        Log.d(TAG, "Duration (us): " + mDurationUs)
    }

    private fun getTrack(mimeType:String):Pair<Int, MediaFormat>?{
        val numTracks = extractor.trackCount
        for( i in 0..numTracks - 1){
           val format =  extractor.getTrackFormat(i)
            if(format.getString(MediaFormat.KEY_MIME).startsWith(mimeType)){
                return Pair(first = i, second = format)
            }
        }
        return null
    }

    var mIsEncoderEOS = false
    var mActualOutputFormat:MediaFormat? = null
    var mEncoderOutputBuffers:Array<out ByteBuffer>? = null
    var mWrittenPresentationTimeUs: Long = 0
    private fun drainEncoder(timeoutUs: Long): Int {
        if (mIsEncoderEOS) return 111
        val result:Int = videoEncoder?.dequeueOutputBuffer(bufferInfo, timeoutUs) ?: 0
        when (result) {
            MediaCodec.INFO_TRY_AGAIN_LATER -> return 0
            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                if (mActualOutputFormat != null)
                    throw RuntimeException("Video output format changed twice.")
                mActualOutputFormat = videoEncoder?.getOutputFormat()
                quedMuxer.setOutputFormat(QueuedMuxer.SampleType.VIDEO, mActualOutputFormat)
                return 0
            }
            MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                mEncoderOutputBuffers = videoEncoder?.getOutputBuffers()
                return 0
            }
        }
        if (mActualOutputFormat == null) {
            throw RuntimeException("Could not determine actual output format.")
        }

        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
            mIsEncoderEOS = true
            bufferInfo.set(0, 0, 0, bufferInfo.flags)
        }
        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
            // SPS or PPS, which should be passed by MediaFormat.
            videoEncoder?.releaseOutputBuffer(result, false)
            return 0
        }
        quedMuxer.writeSampleData(QueuedMuxer.SampleType.VIDEO, mEncoderOutputBuffers!![result], bufferInfo)
        mWrittenPresentationTimeUs = bufferInfo.presentationTimeUs
        videoEncoder?.releaseOutputBuffer(result, false)
        return 0
    }

    private fun decode():Int{

        videoDecoder?.apply {
            val result = dequeueOutputBuffer(bufferInfo, 0)
            when (result) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    return 0
                }
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    println("INFO_OUTPUT_FORMAT_CHANGED")
                    quedMuxer.setOutputFormat(QueuedMuxer.SampleType.VIDEO, outputFormat)
                    return 1
                }
            }
            println("ELSE")

            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                bufferInfo.set(0, 0, 0, bufferInfo.flags)
                return 1111
            }
//            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
//                signalEndOfInputStream()
//                bufferInfo.size = 0
//            }
//            val doRender = bufferInfo.size > 0
//            // NOTE: doRender will block if buffer (of encoder) is full.
//            // Refer: http://bigflake.com/mediacodec/CameraToMpegTest.java.txt
//            videoDecoder?.releaseOutputBuffer(result, doRender)
//            if (doRender) {
//                mDecoderOutputSurfaceWrapper.awaitNewImage()
//                mDecoderOutputSurfaceWrapper.drawImage()
//                mEncoderInputSurfaceWrapper.setPresentationTime(mBufferInfo.presentationTimeUs * 1000)
//                mEncoderInputSurfaceWrapper.swapBuffers()
//            }

        }

        return 1
    }

    private fun createVideoFormat(mediaFormat: MediaFormat, bitRate:Int, frameRate:Int = 30, frameInterval:Int = 3,
                                  colorFormat:Int = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface): MediaFormat{
        val width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH)
        val height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT)
        val mScale = 1
        val targetLonger = mScale * 16 * 16
        val targetShorter = mScale * 16 * 9
        val longer: Int
        val shorter: Int
        val outWidth: Int
        val outHeight: Int
        if (width >= height) {
            longer = width
            shorter = height
            outWidth = targetLonger
            outHeight = targetShorter
        } else {
            shorter = width
            longer = height
            outWidth = targetShorter
            outHeight = targetLonger
        }
        if (longer * 9 != shorter * 16) {
            throw OutputFormatUnavailableException("This video is not 16:9, and is not able to transcode. (" + width + "x" + height + ")")
        }

        return MediaFormat.createVideoFormat("video/avc", outWidth, outHeight).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, frameInterval)
            setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
        }
    }

    private fun createAudioFormat(mediaFormat: MediaFormat, bitRate:Int, accProfile:Int = MediaCodecInfo.CodecProfileLevel.AACObjectLC):MediaFormat{
        return mediaFormat.apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, accProfile)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        }
    }

}

data class EGLParam(val mSurface: Surface){
    private val EGL_RECORDABLE_ANDROID = 0x3142

    var mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    val mEGLContext: EGLContext
    val mEGLSurface: EGLSurface
    init {
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (mEGLDisplay === EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("unable to get EGL14 display")
        }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
            mEGLDisplay = null
            throw RuntimeException("unable to initialize EGL14")
        }
        // Configure EGL for recordable and OpenGL ES 2.0.  We want enough RGB bits
        // to minimize artifacts from possible YUV conversion.
        val attribList = intArrayOf(EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL_RECORDABLE_ANDROID, 1, EGL14.EGL_NONE)
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0, configs.size,
                numConfigs, 0)) {
            throw RuntimeException("unable to find RGB888+recordable ES2 EGL config")
        }
        // Configure context for OpenGL ES 2.0.
        val attrib_list = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        mEGLContext = EGL14.eglCreateContext(mEGLDisplay, configs[0], EGL14.EGL_NO_CONTEXT,
                attrib_list, 0)
        checkEglError("eglCreateContext")
        if (mEGLContext == null) {
            throw RuntimeException("null context")
        }
        // Create a window surface, and attach it to the Surface we received.
        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        mEGLSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, configs[0], mSurface,
                surfaceAttribs, 0)
        checkEglError("eglCreateWindowSurface")
        if (mEGLSurface == null) {
            throw RuntimeException("surface was null")
        }

    }

    /**
     * Checks for EGL errors.
     */
    private fun checkEglError(msg: String) {
        val error: Int = EGL14.eglGetError()
        if (error != EGL14.EGL_SUCCESS) {
            throw RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error))
        }
    }
}

