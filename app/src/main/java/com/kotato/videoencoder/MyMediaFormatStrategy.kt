package com.kotato.videoencoder

import android.media.MediaCodecInfo
import android.media.MediaFormat
import net.ypresto.androidtranscoder.format.MediaFormatExtraConstants
import net.ypresto.androidtranscoder.format.MediaFormatStrategy

/**
 * Created by kotato on 2017/08/22.
 */

data class MyMediaFormat(var videoBitRate:Int,var width: Int,var height: Int, var audioBitRate:Int = 3, var audioChannels:Int = 1, var frameRate:Int = 30, var iFrameInterval: Int = 3)

class MyMediaFormatStrategy(val mVideoBitrate:Int,val width: Int,val height: Int, val mAudioBitrate:Int = 3, val mAudioChannels:Int = 1): MediaFormatStrategy {
    val AUDIO_BITRATE_AS_IS = -1
    val AUDIO_CHANNELS_AS_IS = -1
    private val TAG = "MyMediaFormatStrategy"
    private val LONGER_LENGTH = 1280
    private val SHORTER_LENGTH = 720
    private val DEFAULT_VIDEO_BITRATE = 8000 * 1000 // From Nexus 4 Camera in 720p

    override fun createAudioOutputFormat(inputFormat: MediaFormat?): MediaFormat? {
        if (mAudioBitrate == AUDIO_BITRATE_AS_IS || mAudioChannels == AUDIO_CHANNELS_AS_IS) return null

        // Use original sample rate, as resampling is not supported yet.
        val format = MediaFormat.createAudioFormat(MediaFormatExtraConstants.MIMETYPE_AUDIO_AAC,
                inputFormat?.getInteger(MediaFormat.KEY_SAMPLE_RATE) ?: 0, mAudioChannels)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setInteger(MediaFormat.KEY_BIT_RATE, mAudioBitrate)
        return format
    }

    override fun createVideoOutputFormat(inputFormat: MediaFormat?): MediaFormat? {
        val format = MediaFormat.createVideoFormat("video/avc",width, height)
        format.setInteger(MediaFormat.KEY_BIT_RATE, mVideoBitrate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 3)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        return format
    }

}

