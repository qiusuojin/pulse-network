package com.pulsenetwork.app.util

import java.text.SimpleDateFormat
import java.util.*

/**
 * 时间格式化工具
 */
object TimeUtils {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    private val fullFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * 格式化时间戳为简短时间
     */
    fun formatTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60_000 -> "刚刚"
            diff < 3600_000 -> "${diff / 60_000}分钟前"
            diff < 86400_000 -> timeFormat.format(Date(timestamp))
            diff < 172800_000 -> "昨天 ${timeFormat.format(Date(timestamp))}"
            else -> dateFormat.format(Date(timestamp))
        }
    }

    /**
     * 格式化为完整时间
     */
    fun formatFullTime(timestamp: Long): String {
        return fullFormat.format(Date(timestamp))
    }

    /**
     * 格式化持续时间
     */
    fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }

    /**
     * 格式化文件大小
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }
}
