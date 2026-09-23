/*
 * 文件职责：负责解析WebP格式动图，提取其帧数据和每帧的延迟。
 * 主要内容：WebPFrameExtractor 动图帧解析。
 * 依赖：无
 */
package me.marukon.webp2gif.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.ByteArrayOutputStream

object WebPFrameExtractor {
    private const val TAG = "WebPFrameExtractor"

    data class WebPFrame(
        val bitmap: Bitmap,
        val durationMs: Int
    )

    /**
     * 解析 WebP 二进制字节数组，抓取所有动画帧
     */
    fun extractFrames(webpBytes: ByteArray): List<WebPFrame> {
        val frames = mutableListOf<WebPFrame>()
        var canvasBitmap: Bitmap? = null
        var canvas: android.graphics.Canvas? = null
        val paintBlend = android.graphics.Paint()
        val paintNoBlend = android.graphics.Paint().apply {
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC)
        }
        val paintClear = android.graphics.Paint().apply {
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
        }

        try {
            if (webpBytes.size < 12) {
                Log.e(TAG, "文件字节数不足，无法作为WebP解析")
                return emptyList()
            }

            // 验证 RIFF 与 WEBP 容器头
            val isRiff = webpBytes[0] == 'R'.toByte() && webpBytes[1] == 'I'.toByte() &&
                    webpBytes[2] == 'F'.toByte() && webpBytes[3] == 'F'.toByte()
            val isWebp = webpBytes[8] == 'W'.toByte() && webpBytes[9] == 'E'.toByte() &&
                    webpBytes[10] == 'B'.toByte() && webpBytes[11] == 'P'.toByte()

            if (!isRiff || !isWebp) {
                Log.e(TAG, "非格式合规的 RIFF WEBP 文件")
                return emptyList()
            }

            var parentWidth = 0
            var parentHeight = 0
            var parentHasAlpha = true

            // 回退机制：如果是静态 WebP，可以直接用 BitmapFactory 解码单帧
            var hasAnimChunk = false

            var offset = 12
            while (offset < webpBytes.size - 8) {
                val chunkFourCC = String(webpBytes, offset, 4)
                val chunkSize = readInt32LE(webpBytes, offset + 4)
                if (chunkSize < 0 || offset + 8 + chunkSize > webpBytes.size) {
                    Log.w(TAG, "分块 $chunkFourCC 的大小 $chunkSize 越界，解析跳出")
                    break
                }

                val payloadOffset = offset + 8

                when (chunkFourCC) {
                    "VP8X" -> {
                        if (chunkSize >= 10) {
                            val flags = webpBytes[payloadOffset].toInt()
                            parentHasAlpha = (flags and 0x10) != 0
                            parentWidth = readUInt24LE(webpBytes, payloadOffset + 4) + 1
                            parentHeight = readUInt24LE(webpBytes, payloadOffset + 7) + 1
                            Log.d(TAG, "读取到 VP8X 头: 尺寸=${parentWidth}x${parentHeight}, Alpha=${parentHasAlpha}")
                        }
                    }
                    "ANIM" -> {
                        hasAnimChunk = true
                    }
                    "ANMF" -> {
                        hasAnimChunk = true
                        if (chunkSize >= 16) {
                            val frameX = readUInt24LE(webpBytes, payloadOffset + 0) * 2
                            val frameY = readUInt24LE(webpBytes, payloadOffset + 3) * 2
                            val frameWidth = readUInt24LE(webpBytes, payloadOffset + 6) + 1
                            val frameHeight = readUInt24LE(webpBytes, payloadOffset + 9) + 1
                            val duration = readUInt24LE(webpBytes, payloadOffset + 12)
                            val flags = webpBytes[payloadOffset + 15].toInt() and 0xFF
                            val blendMethod = (flags shr 1) and 1
                            val disposalMethod = flags and 1

                            val subChunksSize = chunkSize - 16
                            if (subChunksSize > 0) {
                                val frameChunksBytes = ByteArray(subChunksSize)
                                System.arraycopy(webpBytes, payloadOffset + 16, frameChunksBytes, 0, subChunksSize)

                                // 自建标准的 static WebP 容器包装这些音频/画幅帧分块
                                val staticWebpBytes = createStaticWebp(
                                    width = frameWidth,
                                    height = frameHeight,
                                    hasAlpha = parentHasAlpha,
                                    chunksBytes = frameChunksBytes
                                )

                                val opt = BitmapFactory.Options().apply {
                                    inScaled = false
                                    inPreferredConfig = Bitmap.Config.ARGB_8888
                                }
                                val subBitmap = BitmapFactory.decodeByteArray(staticWebpBytes, 0, staticWebpBytes.size, opt)
                                if (subBitmap != null) {
                                    if (canvasBitmap == null) {
                                        val w = if (parentWidth > 0) parentWidth else frameWidth
                                        val h = if (parentHeight > 0) parentHeight else frameHeight
                                        canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                        canvas = android.graphics.Canvas(canvasBitmap)
                                    }

                                    val paint = if (blendMethod == 0) paintBlend else paintNoBlend
                                    canvas?.drawBitmap(subBitmap, frameX.toFloat(), frameY.toFloat(), paint)

                                    val compositedBitmap = canvasBitmap.copy(Bitmap.Config.ARGB_8888, true)
                                    frames.add(WebPFrame(compositedBitmap, if (duration <= 10) 100 else duration))

                                    if (disposalMethod == 1) {
                                        canvas?.drawRect(
                                            frameX.toFloat(),
                                            frameY.toFloat(),
                                            (frameX + frameWidth).toFloat(),
                                            (frameY + frameHeight).toFloat(),
                                            paintClear
                                        )
                                    }
                                    subBitmap.recycle()
                                } else {
                                    Log.e(TAG, "解析 ANMF 数据帧失败")
                                }
                            }
                        }
                    }
                }

                // Chunk 按偶数字节对齐
                offset += 8 + chunkSize + (chunkSize % 2)
            }

            // 如果文件本身是无动画的，或者由于特殊打包没读到 ANMF 块但 BitmapFactory 能解
            if (frames.isEmpty() || !hasAnimChunk) {
                val opt = BitmapFactory.Options().apply { inScaled = false }
                val bitmap = BitmapFactory.decodeByteArray(webpBytes, 0, webpBytes.size, opt)
                if (bitmap != null) {
                    frames.add(WebPFrame(bitmap, 100))
                    Log.d(TAG, "使用静态 WebP 单帧解码兜底成功")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "WebP 提取进程异常: ${e.message}", e)
        } finally {
            canvasBitmap?.recycle()
        }
        return frames
    }

    private fun createStaticWebp(width: Int, height: Int, hasAlpha: Boolean, chunksBytes: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        
        // 构建 10 字节的真实 VP8X 数据头
        val vp8xPayload = ByteArray(10)
        vp8xPayload[0] = if (hasAlpha) 0x10.toByte() else 0x00.toByte() // Flags: 拥有 Alpah 透明层
        
        // 写入 Width (24bit: width - 1)
        val wMinus1 = width - 1
        vp8xPayload[4] = (wMinus1 and 0xFF).toByte()
        vp8xPayload[5] = ((wMinus1 shr 8) and 0xFF).toByte()
        vp8xPayload[6] = ((wMinus1 shr 16) and 0xFF).toByte()

        // 写入 Height (24bit: height - 1)
        val hMinus1 = height - 1
        vp8xPayload[7] = (hMinus1 and 0xFF).toByte()
        vp8xPayload[8] = ((hMinus1 shr 8) and 0xFF).toByte()
        vp8xPayload[9] = ((hMinus1 shr 16) and 0xFF).toByte()

        // 计算 RIFF 总宽容大小
        // "WEBP" (4) + VP8X Header (8) + VP8X Payload (10) + frameChunks (chunksBytes.size)
        val totalSize = 4 + 8 + 10 + chunksBytes.size

        // 1. 写 RIFF 签名
        bos.write('R'.code)
        bos.write('I'.code)
        bos.write('F'.code)
        bos.write('F'.code)

        // 2. 写文件大小
        bos.write(totalSize and 0xFF)
        bos.write((totalSize shr 8) and 0xFF)
        bos.write((totalSize shr 16) and 0xFF)
        bos.write((totalSize shr 24) and 0xFF)

        // 3. 写 WEBP
        bos.write('W'.code)
        bos.write('E'.code)
        bos.write('B'.code)
        bos.write('P'.code)

        // 4. 写 VP8X 头部
        bos.write('V'.code)
        bos.write('P'.code)
        bos.write('8'.code)
        bos.write('X'.code)
        bos.write(10)
        bos.write(0)
        bos.write(0)
        bos.write(0)
        bos.write(vp8xPayload)

        // 5. 写入帧分块实体数据段 (如 ALPH, VP8 等)
        bos.write(chunksBytes)

        return bos.toByteArray()
    }

    private fun readInt32LE(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readUInt24LE(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16)
    }
}
