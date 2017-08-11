package com.kotato.videoencoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.nio.ByteBuffer


class VideoEncoderCore(val width: Int, val height:Int, val bitRate:Int, val outputFile:File){
    private val MIME_TYPE = "video/avc"    // H.264 Advanced Video Coding
    private val FRAME_RATE = 30               // 30fps
    private val IFRAME_INTERVAL = 5           // 5 seconds between I-frames
    private val TAG = "VideoEncoderCore"

    val mBufferInfo = MediaCodec.BufferInfo()
    val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL)
    }

    val mEncoder = MediaCodec.createEncoderByType(MIME_TYPE).apply {
        configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    }

    val mInputSurface = mEncoder.createInputSurface()

    val mMuxer = MediaMuxer(outputFile.toString(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

    var mTrackIndex = -1
    var mMuxerStarted = false

    init {
        Log.d(TAG, "format: " + format);
        mEncoder.start()
    }

    fun relese(){
        mEncoder?.let {
            mEncoder.stop()
            mEncoder.release()
        }

        mMuxer?.let {
            mMuxer.stop()
            mMuxer.release()
        }

    }

    val TIMEOUT_USEC = 10000L
    fun drainEncoder(endOfStream: Boolean, bufferId: Int){
        if(endOfStream){
            mEncoder.signalEndOfInputStream()
        }

        var encoderOutputBuffers = mEncoder.getOutputBuffer(bufferId)

        loop@ while (true){
            var encoderStatus = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC)
            when {
                encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER -> if(!endOfStream) return@loop
                encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (mMuxerStarted) {
                        throw RuntimeException("format changed twice")
                    }
                    val newFormat = mEncoder.outputFormat
                    Log.d(TAG, "encoder output format changed: " + newFormat)

                    // now that we have the Magic Goodies, start the muxer
                    mTrackIndex = mMuxer.addTrack(newFormat)
                    mMuxer.start()
                    mMuxerStarted = true
                }
                encoderStatus < 0 -> Log.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus)
                else -> {
                    val encodedData = encoderOutputBuffers[encoderStatus] ?: throw RuntimeException("encoderOutputBuffer " + encoderStatus + " was null")

                    if (mBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG !== 0) {
                        // The codec config data was pulled out and fed to the muxer when we got
                        // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                        Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG")
                        mBufferInfo.size = 0
                    }

                    if (mBufferInfo.size !== 0) {
                        if (!mMuxerStarted) {
                            throw RuntimeException("muxer hasn't started")
                        }

                        mMuxer.writeSampleData(mTrackIndex, encoderOutputBuffers, mBufferInfo)
                    }

                    mEncoder.releaseOutputBuffer(encoderStatus, false)

                    if (mBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM !== 0) {
                        if (!endOfStream) {
                            Log.w(TAG, "reached end of stream unexpectedly")
                        }
                        return@loop      // out of while
                    }
                }

            }
        }

    }



}