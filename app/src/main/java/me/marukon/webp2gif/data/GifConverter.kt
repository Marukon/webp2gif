/*
 * 文件职责：负责动图表情包转换的核心服务，包括 WebP 转 GIF 流程。
 * 主要内容：GifConverter 转换服务，支持异步调用并向 UI 回传转换进度。
 * 依赖：依赖 WebPFrameExtractor 进行帧拆分，调用 com.squareup.gifencoder.GifEncoder 进行 GIF 拼装。
 */
package me.marukon.webp2gif.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.bumptech.glide.gifencoder.AnimatedGifEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object GifConverter {
    private const val TAG = "GifConverter"

    /**
     * 将输入的 WebP（动图或静态图）转换输出为 .gif 文件，支持进度反馈。
     */
    suspend fun convertWebpToGif(
        context: Context,
        webpUri: Uri,
        outputFile: File,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(webpUri)
                ?: return@withContext Result.failure(Exception("无法访问或打开选中的 WebP 格式源文件"))

            val bytes = inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) {
                return@withContext Result.failure(Exception("文件字节为空，请重新选择文件"))
            }

            Log.d(TAG, "开始解析 WebP, 总字节大小: ${bytes.size}")
            withContext(Dispatchers.Main) { onProgress(0.15f) }

            // 解析动图中的所有帧
            val frames = WebPFrameExtractor.extractFrames(bytes)
            withContext(Dispatchers.Main) { onProgress(0.4f) }

            if (frames.isEmpty()) {
                return@withContext Result.failure(Exception("无法解析出有效的图像帧"))
            }

            Log.d(TAG, "解析出帧数量: ${frames.size}，开始输出拼接 GIF")

            val outputStream = outputFile.outputStream()
            try {
                val encoder = AnimatedGifEncoder()
                encoder.setRepeat(0) // 0 means loop continuously
                encoder.setDispose(2) // 2: Restore to background (crucial for transparent frames)

                if (!encoder.start(outputStream)) {
                    return@withContext Result.failure(Exception("无法启动 GIF 编码器"))
                }

                val totalFrames = frames.size
                for (i in 0 until totalFrames) {
                    val frame = frames[i]
                    val bitmap = frame.bitmap

                    val delay = if (frame.durationMs < 15) 100 else frame.durationMs
                    encoder.setDelay(delay)

                    if (!encoder.addFrame(bitmap)) {
                        Log.e(TAG, "添加帧 $i 失败")
                    }

                    // 立即回收这一帧以节约内存
                    bitmap.recycle()

                    // 计算进度 40% - 95% 之间
                    val progress = 0.4f + 0.55f * (i + 1) / totalFrames
                    withContext(Dispatchers.Main) {
                        onProgress(progress)
                    }
                }

                if (!encoder.finish()) {
                    Log.e(TAG, "完成 GIF 编码失败")
                }
            } finally {
                outputStream.close()
            }

            Log.d(TAG, "GIF 编码拼合完成: ${outputFile.absolutePath}, 大小: ${outputFile.length()} 字节")
            withContext(Dispatchers.Main) { onProgress(1.0f) }

            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "WebP 转 GIF 转换失败: ${e.message}", e)
            Result.failure(e)
        }
    }
}
