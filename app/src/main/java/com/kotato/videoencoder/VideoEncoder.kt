package com.kotato.videoencoder

import android.media.*
import android.media.browse.MediaBrowser
import java.io.File
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.security.cert.CertPath

/**
 * Created by kotato on 2017/08/11.
 */


class VideoEncoder(val file: File, val outputPath: String){
    var extractor: MediaExtractor = MediaExtractor().apply {
        setDataSource(file.inputStream().fd)
    }
    var muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

    fun main(){
        val videoFormat = getTrack("video/")
        videoFormat?.apply {
            createVideoFormat(second, 8000 * 1000)
        }
        val audioFormat = getTrack("audio/")
        audioFormat?.apply {
            createAudioFormat(second, 8000 * 1000)
        }
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

    private fun createVideoFormat(mediaFormat: MediaFormat, bitRate:Int, frameRate:Int = 30, frameInterval:Int = 3,
                                  colorFormat:Int = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface): MediaFormat{
        return mediaFormat.apply {
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

