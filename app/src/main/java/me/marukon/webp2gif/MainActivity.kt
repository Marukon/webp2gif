/*
 * 文件职责：应用的主界面，持有所有组件层叠与交互控制逻辑。
 * 主要内容：MainActivity, MainViewModel，以及相关的 Compose 界面组件。
 * 依赖：依赖 data 模块中的 GifConverter 和 update 模块中的 UpdateRepository。
 */
package me.marukon.webp2gif

import android.content.Context
import android.content.Intent
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.marukon.webp2gif.data.GifConverter
import me.marukon.webp2gif.ui.theme.BubuTheme
import me.marukon.webp2gif.ui.theme.BubuPinkPrimary
import me.marukon.webp2gif.ui.theme.BubuPinkSecondary
import me.marukon.webp2gif.ui.theme.BubuWhiteCream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BubuTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.systemBars
                ) { innerPadding ->
                    ConverterAppScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

// UI 状态定义
data class UiState(
    val selectedUri: Uri? = null,
    val fileName: String = "",
    val fileSizeText: String = "",
    val isConverting: Boolean = false,
    val convertProgress: Float = 0f,
    val convertedFile: File? = null,
    val showSuccess: Boolean = false,
    val errorMessage: String? = null
)

class MainViewModel : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun selectFile(uri: Uri?, context: Context) {
        if (uri == null) return
        viewModelScope.launch {
            var name = "未知文件"
            var sizeText = ""
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) name = cursor.getString(nameIndex)
                        if (sizeIndex != -1) {
                            val bytes = cursor.getLong(sizeIndex)
                            sizeText = String.format("%.1f KB", bytes / 1024f)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "读取原文件名异常", e)
            }

            _state.value = _state.value.copy(
                selectedUri = uri,
                fileName = name,
                fileSizeText = sizeText,
                convertedFile = null,
                showSuccess = false,
                errorMessage = null
            )
        }
    }

    fun startConversion(context: Context) {
        val uri = _state.value.selectedUri ?: return
        _state.value = _state.value.copy(isConverting = true, convertProgress = 0f, errorMessage = null)

        viewModelScope.launch {
            // 输入文件重命名（例如表情.webp -> 表情_converted.gif）
            val originalName = _state.value.fileName
            val cleanName = originalName.substringBeforeLast(".")
            val outputFileName = "${cleanName}_converted.gif"
            
            val cacheFile = File(context.cacheDir, outputFileName)
            if (cacheFile.exists()) {
                cacheFile.delete()
            }

            val result = GifConverter.convertWebpToGif(context, uri, cacheFile) { progress ->
                _state.value = _state.value.copy(convertProgress = progress)
            }

            result.fold(
                onSuccess = { file ->
                    _state.value = _state.value.copy(
                        isConverting = false,
                        convertedFile = file,
                        showSuccess = true
                    )
                    // 自动保存到系统相册
                    viewModelScope.launch(Dispatchers.IO) {
                        val saveResult = saveGifToGallery(context, file)
                        launch(Dispatchers.Main) {
                            if (saveResult) {
                                Toast.makeText(context, "已成功自动保存到手机系统相册！", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "自动保存到相册失败，但可通过'快速分享'直接发送", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                onFailure = { throwable ->
                    _state.value = _state.value.copy(
                        isConverting = false,
                        errorMessage = throwable.message ?: "转换内部故障"
                    )
                }
            )
        }
    }

    fun autoCleanCache(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = context.cacheDir
                val files = cacheDir.listFiles()
                if (files != null) {
                    for (file in files) {
                        if (file.name.endsWith(".gif") || file.name.endsWith(".webp")) {
                            // 保留当前选定和转换的文件，其余一律清除
                            val currentConvertedPath = _state.value.convertedFile?.absolutePath
                            if (file.absolutePath != currentConvertedPath) {
                                file.delete()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Auto clean cache error", e)
            }
        }
    }

    fun clearState() {
        _state.value = UiState()
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ConverterAppScreen(modifier: Modifier = Modifier, viewModel: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            viewModel.selectFile(uri, context)
        }
    )

    // 页面加载触发自动清理本地缓存
    LaunchedEffect(Unit) {
        viewModel.autoCleanCache(context)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App 标志和主标题区域
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_bubu_logo_1782102546362),
                contentDescription = "布布Logo",
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "布布表情包转换器",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "超萌 WebP 动图极速转 GIF 工具",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // 核心显示区域：预览卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(290.dp),
            shape = RoundedCornerShape(32.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val selectedUri = state.selectedUri
                if (selectedUri != null) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        AnimatedMemePreview(
                            uri = selectedUri,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = state.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (state.fileSizeText.isNotEmpty()) {
                            Text(
                                text = "文件大小: ${state.fileSizeText}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(28.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "未选择",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Text(
                            text = "点击下方「选择表情」导入 WebP 动图\n微信/QQ 个人导出的超级表情瞬间转 GIF",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }

        // 常规操作主按钮（一行展示，全圆角药丸组件，拒绝换行）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { filePickerLauncher.launch(arrayOf("image/webp", "image/gif", "image/*")) },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = "上传", modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "选择表情", maxLines = 1, softWrap = false, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { viewModel.startConversion(context) },
                enabled = (state.selectedUri != null && !state.isConverting),
                modifier = Modifier.weight(1f).height(48.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "转换", modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "开始转换", maxLines = 1, softWrap = false, fontWeight = FontWeight.Bold)
            }
        }

        // 自动静默管理缓存以维护极速平稳运行

        // 转换中的进度动画和进度条
        AnimatedVisibility(
            visible = state.isConverting,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "正在拆帧并拼合为 GIF...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    LinearProgressIndicator(
                        progress = { state.convertProgress },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${(state.convertProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        // 错误提示区
        AnimatedVisibility(visible = state.errorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = state.errorMessage ?: "",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // 转换成功展示与多级处理操作
        AnimatedVisibility(
            visible = state.showSuccess && state.convertedFile != null,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "超级布布成功完成转换为 GIF！",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = "文件已暂存：${state.convertedFile?.name ?: ""}\n大小为: ${String.format("%.1f KB", (state.convertedFile?.length() ?: 0L) / 1024f)}",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )

                    // 操作按钮：快速分享（转换完成后已自动保存到系统相册）
                    Button(
                        onClick = {
                            val file = state.convertedFile ?: return@Button
                            val shareUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/gif"
                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "发送布布表情包"))
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "分享")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "快速分享", maxLines = 1, softWrap = false, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 底部版本区
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
        ) {
            Text(
                text = "布布表情包转换器 v1.0.0 © 2026",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }

    // 更新提醒对话框
    if (false) {}

    // 批量处理说明对话框
    if (false) {}
}

/**
 * 原生动图表情包播放组件。在 API 28+ 使用系统自带 `ImageDecoder` 完美实现 GIF/Webp 动画播放
 */
@Composable
fun AnimatedMemePreview(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        },
        update = { imageView ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    val drawable = ImageDecoder.decodeDrawable(source)
                    imageView.setImageDrawable(drawable)
                    if (drawable is AnimatedImageDrawable) {
                        drawable.start()
                    }
                } catch (e: Exception) {
                    Log.e("AnimatedMemePreview", "ImageDecoder decode Error", e)
                    imageView.setImageURI(uri) // 回退
                }
            } else {
                imageView.setImageURI(uri)
            }
        },
        modifier = modifier
    )
}

/**
 * 将生成的 GIF 表情包文件保存到公共 MediaStore 画廊，完美适配 Q (Scoped Storage)
 */
fun saveGifToGallery(context: Context, gifFile: File): Boolean {
    return try {
        val resolver = context.contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, gifFile.name)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/gif")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/BubuConverter")
        }
        
        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return false
            
        resolver.openOutputStream(uri)?.use { outputStream ->
            gifFile.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        true
    } catch (e: Exception) {
        Log.e("saveGifToGallery", "保存系统画廊失败", e)
        false
    }
}
