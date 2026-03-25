package com.example.cardnote.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * 图片持久化管理器
 *
 * 核心原则：用户选择图片（相册/拍照）后，立即将文件复制到
 * App 私有目录 [filesDir]/note_images/，数据库只存这个内部路径。
 * 这样，原始相册文件删除、权限撤销均不影响 App 内的图片显示。
 *
 * 存储路径：<filesDir>/note_images/<uuid>.jpg
 * 生命周期：随 App 卸载自动清理，无外部垃圾残留。
 */
object ImageStorageManager {

    private const val DIR_NAME = "note_images"

    /** 返回私有图片目录，不存在则创建 */
    private fun getImageDir(context: Context): File {
        return File(context.filesDir, DIR_NAME).also { it.mkdirs() }
    }

    /**
     * 将外部 URI（相册 / 拍照临时 URI）复制到 App 私有目录。
     * @return 内部文件的绝对路径字符串，失败返回 null
     */
    suspend fun copyToPrivateStorage(context: Context, sourceUri: Uri): String? =
        withContext(Dispatchers.IO) {
            try {
                val dir = getImageDir(context)
                val destFile = File(dir, "${UUID.randomUUID()}.jpg")

                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext null

                destFile.absolutePath
            } catch (e: IOException) {
                e.printStackTrace()
                null
            }
        }

    /**
     * 批量复制，返回成功的路径列表（过滤失败项）
     */
    suspend fun copyAllToPrivateStorage(context: Context, uris: List<Uri>): List<String> =
        uris.mapNotNull { copyToPrivateStorage(context, it) }

    /**
     * 删除笔记对应的所有私有图片文件（笔记删除时调用）
     */
    suspend fun deleteImages(imagePaths: List<String>) =
        withContext(Dispatchers.IO) {
            imagePaths.forEach { path ->
                try { File(path).delete() } catch (_: Exception) {}
            }
        }

    /**
     * 删除单张图片
     */
    suspend fun deleteImage(path: String) = withContext(Dispatchers.IO) {
        try { File(path).delete() } catch (_: Exception) {}
    }

    /**
     * 扫描私有目录中不被任何笔记引用的孤立文件并删除（可选清理任务）
     */
    suspend fun pruneOrphanedImages(context: Context, allReferencedPaths: Set<String>) =
        withContext(Dispatchers.IO) {
            getImageDir(context).listFiles()?.forEach { file ->
                if (file.absolutePath !in allReferencedPaths) {
                    file.delete()
                }
            }
        }
}
