package com.pulsenetwork.app.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 权限请求工具
 */
object PermissionUtils {

    // 录音权限
    val RECORD_AUDIO_PERMISSION = arrayOf(Manifest.permission.RECORD_AUDIO)

    // 存储权限
    val STORAGE_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        )
    } else {
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    // 网络权限（通常不需要运行时请求）
    val NETWORK_PERMISSIONS = arrayOf(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.ACCESS_WIFI_STATE
    )

    /**
     * 检查是否有指定权限
     */
    fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 检查是否有录音权限
     */
    fun hasRecordAudioPermission(context: Context): Boolean {
        return hasPermissions(context, RECORD_AUDIO_PERMISSION)
    }

    /**
     * 检查是否有存储权限
     */
    fun hasStoragePermission(context: Context): Boolean {
        return hasPermissions(context, STORAGE_PERMISSIONS)
    }

    /**
     * 请求权限
     */
    fun requestPermissions(activity: Activity, permissions: Array<String>, requestCode: Int) {
        ActivityCompat.requestPermissions(activity, permissions, requestCode)
    }

    /**
     * 请求录音权限
     */
    fun requestRecordAudioPermission(activity: Activity, requestCode: Int = 1001) {
        requestPermissions(activity, RECORD_AUDIO_PERMISSION, requestCode)
    }

    /**
     * 请求存储权限
     */
    fun requestStoragePermission(activity: Activity, requestCode: Int = 1002) {
        requestPermissions(activity, STORAGE_PERMISSIONS, requestCode)
    }

    /**
     * 检查是否应该显示权限说明
     */
    fun shouldShowRequestPermissionRationale(
        activity: Activity,
        permission: String
    ): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }

    /**
     * 处理权限请求结果
     */
    fun onRequestPermissionsResult(
        grantResults: IntArray,
        onGranted: () -> Unit,
        onDenied: () -> Unit
    ) {
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            onGranted()
        } else {
            onDenied()
        }
    }
}
