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
import android.app.Activity
import android.icu.lang.UCharacter
import android.media.MediaCodecInfo
import android.os.SystemClock
import android.util.Log
import android.widget.*
import java.lang.Exception
import net.ypresto.androidtranscoder.format.MediaFormatExtraConstants
import net.ypresto.androidtranscoder.format.MediaFormatStrategy
import net.ypresto.androidtranscoder.format.OutputFormatUnavailableException
import java.io.IOException
import kotlin.system.measureTimeMillis


private val OUTPUT_DIR = Environment.getExternalStorageDirectory()


class MainActivity : AppCompatActivity() {
    data class ViewHolder(val screenSpinner: Spinner, val videoSpinner: Spinner, val audioSpinner: Spinner,
                          val inputFileLabel: TextView, val compeleteText: TextView, val millsecText: TextView)

    private val VIDEO_SELECT_INTENT = 1000
    lateinit var codec:MediaCodec
    lateinit var holder:ViewHolder
    lateinit var progressBar:ProgressBar


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val spinner = findViewById(R.id.screen_spinner) as Spinner
        val adapter = ArrayAdapter.createFromResource(this, R.array.resolution, android.R.layout.simple_spinner_item)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val videSpinner = findViewById(R.id.video_bitrate_spinner) as Spinner
        val videoAdapter = ArrayAdapter.createFromResource(this, R.array.video_bitrate, android.R.layout.simple_spinner_item)
        videoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        videSpinner.adapter = videoAdapter

        val audioSpinner = findViewById(R.id.audio_bitrate_spinner) as Spinner
        val audioAdapter = ArrayAdapter.createFromResource(this, R.array.video_bitrate, android.R.layout.simple_spinner_item)
        audioAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        audioSpinner.adapter = audioAdapter

        progressBar = findViewById(R.id.progressBar) as ProgressBar
        progressBar.max = 100
        progressBar.progress = 0


        holder = ViewHolder(spinner, videSpinner, audioSpinner, findViewById(R.id.file_name) as TextView,
                findViewById(R.id.complete_text) as TextView, findViewById(R.id.millSecText) as TextView)

    }

    fun selectVideo(view: View?){
        ActivityCompat.requestPermissions(this, listOf<String>(Manifest.permission.WRITE_EXTERNAL_STORAGE).toTypedArray(),10)
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "video/*"
        startActivityForResult(intent,VIDEO_SELECT_INTENT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && intent != null) {
            var uri = data?.data as Uri
            uri.let {
                //クラウド上のファイルなどが渡された場合は変換ができないため一度コンバートする
                val uriText = getPathFromUri(this, uri)
                Log.d("Select Media", uriText)
                holder.inputFileLabel.text = uriText
            }
        }

    }

    fun test(view: View? = null){
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
//            val outputFile = File(OUTPUT_DIR.absolutePath + "/test.mp4")
//            val file = File(OUTPUT_DIR.absolutePath + "/waterfall-free-video1.mp4")
//            VideoEncoder(file,outputFile.path).main()
//            MediaTranscoderEngine().apply {
//                setDataSource(file.inputStream().fd)
//                transcodeVideo(OUTPUT_DIR.absolutePath + "/test1.mp4",  MediaFormatStrategyPresets.createAndroid720pStrategy(8000 * 1000, 128 * 1000, 1))
////                transcodeVideo(OUTPUT_DIR.absolutePath + "/test1.mp4",  MediaFormatStrategyPresets.createExportPreset960x540Strategy())
//
//            }
            println("end")
        }

//        codec = MediaCodec.createEncoderByType("video/mp4v-es")
//        codec.start()
//        val file = File(this.getFilesDir(), "test")
//        val encoder = com.kotato.videoencoder.VideoEncoderCore(100, 100, 900*100, file)
//        encoder.drainEncoder(true, codec.dequeueInputBuffer(10000L))
    }


    fun encodeVideo(view: View?){
        progressBar.progress = 0
        holder.compeleteText.text = ""
        holder.millsecText.text = ""

        val file = File(holder.inputFileLabel.text.toString())
        val selectedStr = holder.screenSpinner.selectedItem.toString().split("×")
        val width = selectedStr[0].toInt()
        val height = selectedStr[1].toInt()
        val outputFile = File(filesDir.absolutePath+"/$width x$height _"+file.name)

        val videoBitRateStr = holder.videoSpinner.selectedItem.toString().replace("k","")
        val videoBitRate = videoBitRateStr.toInt() * 1000
        val audioBitRateStr = holder.audioSpinner.selectedItem.toString().replace("k","")
        val audioBitRate = audioBitRateStr.toInt() * 1000

        val resorver = contentResolver
        val parcelFileDescripter = resorver.openFileDescriptor(Uri.fromFile(file), "r")
        val fileDescriptor = parcelFileDescripter.fileDescriptor
        val startTime = SystemClock.uptimeMillis()

        val context = this

        val listener = object : MediaTranscoder.Listener {

            override fun onTranscodeCompleted() {
                Log.d("Time", "transcoding took " + (SystemClock.uptimeMillis() - startTime) + "ms");
                holder.millsecText.text = "It took "+(SystemClock.uptimeMillis() - startTime) + " ms"
                onTranscodeFinished(true, "Completed.", parcelFileDescripter)
                progressBar.progress = 100
            }

            override fun onTranscodeProgress(progress: Double) {
                progressBar.progress = (progress * 100).toInt()
            }

            override fun onTranscodeCanceled() {
                onTranscodeFinished(false, "Canceled.", parcelFileDescripter)
            }

            override fun onTranscodeFailed(exception: Exception?) {
                onTranscodeFinished(false, "Failed.", parcelFileDescripter)
            }

            private fun onTranscodeFinished(isSuccess: Boolean, toastMessage: String, parcelFileDescriptor: ParcelFileDescriptor) {
                try {
                    parcelFileDescriptor.close()
                } catch (e: IOException) {
                    Log.w("Error while closing", e)
                }

                if(isSuccess){
                    Toast.makeText(context, "Success", Toast.LENGTH_LONG).show()
                    holder.compeleteText.text = "OutPut " + outputFile.absolutePath
                }
            }
        }

        MediaTranscoder.getInstance().transcodeVideo(fileDescriptor, outputFile.absolutePath,
                    MyMediaFormatStrategy(videoBitRate, width, height, audioBitRate, 1), listener)

    }




}
