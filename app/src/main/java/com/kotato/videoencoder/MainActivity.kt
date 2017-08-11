package com.kotato.videoencoder

import android.Manifest
import android.media.MediaCodec
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import java.io.File
import android.Manifest.permission
import android.content.Intent
import android.support.v4.app.ActivityCompat
import android.content.pm.PackageManager
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.support.v4.content.ContextCompat
import net.ypresto.androidtranscoder.MediaTranscoder
import net.ypresto.androidtranscoder.format.MediaFormatStrategyPresets
import java.io.FileDescriptor
import java.nio.ByteBuffer
import android.R.attr.data
import android.os.SystemClock
import android.util.Log
import java.lang.Exception
import android.widget.Toast
import android.widget.ProgressBar
import java.io.IOException


private val OUTPUT_DIR = Environment.getExternalStorageDirectory()


class MainActivity : AppCompatActivity() {
    lateinit var codec:MediaCodec
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
//        codec = MediaCodec.createByCodecName("video/")
//        codec.start()

    }

    fun encode(view: View){
        val permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)

        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, listOf<String>(Manifest.permission.WRITE_EXTERNAL_STORAGE).toTypedArray(),10)
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                // パーミッションが必要であることを明示するアプリケーション独自のUIを表示
            }

        } else {
            // 許可済みの場合、もしくはAndroid 6.0以前
            // パーミッションが必要な処理
//            EncodeAndMuxTest().testEncodeVideoToMp4()
//            var extractor: MediaExtractor? = mediaExtractor()
//            extractor = null
//
//            encodeTest()
            val outputFile = File(OUTPUT_DIR.absolutePath + "/test.mp4")
            val file = File(OUTPUT_DIR.absolutePath + "/waterfall-free-video1.mp4")

            VideoEncoder(file,outputFile.path).main()
        }
//        val file = File(this.getFilesDir(), "test")
//        val encoder = com.kotato.videoencoder.VideoEncoderCore(100, 100, 900*100, file)
//        encoder.drainEncoder(true, codec.dequeueInputBuffer(10000L))
    }

    private fun encodeTest() {
        val outputFile = File(OUTPUT_DIR.absolutePath + "/test.mp4")
        val file = File(OUTPUT_DIR.absolutePath + "/waterfall-free-video1.mp4")

        val resorver = contentResolver
        val parcelFileDescripter = resorver.openFileDescriptor(Uri.fromFile(file), "r")
        val fileDescriptor = parcelFileDescripter.fileDescriptor
        val startTime = SystemClock.uptimeMillis()
        val listener = object : MediaTranscoder.Listener {
            /**
             * Called when transcode completed.
             */
            override fun onTranscodeCompleted() {
                Log.d("Time", "transcoding took " + (SystemClock.uptimeMillis() - startTime) + "ms");
                onTranscodeFinished(true, "Completed.", parcelFileDescripter)
            }

            /**
             * Called to notify progress.

             * @param progress Progress in [0.0, 1.0] range, or negative value if progress is unknown.
             */
            override fun onTranscodeProgress(progress: Double) {
            }

            /**
             * Called when transcode canceled.
             */
            override fun onTranscodeCanceled() {
                onTranscodeFinished(false, "Canceled.", parcelFileDescripter)
            }

            /**
             * Called when transcode failed.

             * @param exception Exception thrown from [MediaTranscoderEngine.transcodeVideo].
             * *                  Note that it IS NOT [java.lang.Throwable]. This means [java.lang.Error] won't be caught.
             */
            override fun onTranscodeFailed(exception: Exception?) {
                onTranscodeFinished(false, "Faild.", parcelFileDescripter)
            }

            private fun onTranscodeFinished(isSuccess: Boolean, toastMessage: String, parcelFileDescriptor: ParcelFileDescriptor) {
                try {
                    parcelFileDescriptor.close()
                } catch (e: IOException) {
                    Log.w("Error while closing", e)
                }

            }
        }

        MediaTranscoder.getInstance().transcodeVideo(fileDescriptor, outputFile.getAbsolutePath(),
                MediaFormatStrategyPresets.createAndroid720pStrategy(8000 * 1000, 128 * 1000, 1), listener);
    }

    private fun mediaExtractor(): MediaExtractor? {
        var extractor: MediaExtractor? = MediaExtractor()
        val file = File(OUTPUT_DIR.absolutePath + "/waterfall-free-video1.mp4")
        //            println(file.list().toList())
        extractor?.let {
            it.setDataSource(file.inputStream().fd)
            val numTracks = it.trackCount
            println(numTracks)
            for (i in 0..numTracks - 1) {
                val format = it.getTrackFormat(i)
                val mine = format.getString(MediaFormat.KEY_MIME)
                println(mine)
                it.selectTrack(i)
            }

            val size = file.readBytes().size
            val inputBuffer = ByteBuffer.allocate(size)
            while (it.readSampleData(inputBuffer, 0) >= 0) {
                val trackIndex = it.sampleTrackIndex
                val presentationTImeUs = it.sampleTime
                println(trackIndex.toString() + "/" + presentationTImeUs)

                it.advance()
            }

            it.release()
        }
        return extractor
    }


}

