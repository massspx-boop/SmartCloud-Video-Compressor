package com.scvc.app

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.*
import java.io.File

/** Offline H.264/AAC MP4 transcode using Android Media3 and device codecs. */
@OptIn(UnstableApi::class)
object VideoTranscoder {
    fun start(
        context: Context, source: File, destination: File, targetHeight: Int,
        onProgress: (Int) -> Unit, onCompleted: (ExportResult) -> Unit,
        onError: (Throwable) -> Unit
    ): Transformer {
        require(targetHeight == 480 || targetHeight == 720 || targetHeight == 1080)
        val retriever = MediaMetadataRetriever()
        val originalHeight = try {
            retriever.setDataSource(source.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: error("Video track not found")
        } finally { retriever.release() }
        val outputHeight = minOf(originalHeight, targetHeight).coerceAtLeast(2)
        val effect = Presentation.createForHeight(outputHeight)
        val item = EditedMediaItem.Builder(MediaItem.fromUri(source.absolutePath))
            .setEffects(Effects(emptyList(), listOf(effect))).build()
        val holder = ProgressHolder()
        val handler = Handler(Looper.getMainLooper())
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    onProgress(100); onCompleted(result)
                }
                override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                    onError(exception)
                }
            }).build()
        transformer.start(item, destination.absolutePath)
        val poll = object : Runnable {
            override fun run() {
                val state = transformer.getProgress(holder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
                if (state != Transformer.PROGRESS_STATE_NOT_STARTED &&
                    state != Transformer.PROGRESS_STATE_NO_TRANSFORMATION) handler.postDelayed(this, 500)
            }
        }
        handler.post(poll)
        return transformer
    }
}
