package com.halo.yunquevoice.ui

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable

/** 多通道读取系统壁纸，兼容 HyperOS/MIUI 等不同实现。文件直读优先，绕开服务层缓存。 */
object WallpaperReader {

    fun load(context: Context): Bitmap? {
        val wm = WallpaperManager.getInstance(context)
        // 1. 文件直读（无服务缓存，换壁纸立即可见）
        runCatching {
            val pfd = wm.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)
            if (pfd != null) {
                val bitmap = BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)
                pfd.close()
                if (bitmap != null) {
                    android.util.Log.i("YunqueVoice", "壁纸读取：FLAG_SYSTEM 文件直读 ${bitmap.width}x${bitmap.height}")
                    return bitmap
                }
            }
        }
        // 2. getDrawable（部分 ROM 的兼容通道，可能命中服务缓存）
        runCatching {
            val drawable = wm.drawable
            if (drawable is BitmapDrawable) {
                android.util.Log.i("YunqueVoice", "壁纸读取：getDrawable 通道 ${drawable.bitmap.width}x${drawable.bitmap.height}")
                return drawable.bitmap
            }
        }
        // 3. 锁屏壁纸兜底
        runCatching {
            val pfd = wm.getWallpaperFile(WallpaperManager.FLAG_LOCK)
            if (pfd != null) {
                val bitmap = BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)
                pfd.close()
                if (bitmap != null) return bitmap
            }
        }
        android.util.Log.w("YunqueVoice", "壁纸读取：全部通道失败，使用纯渐变背景")
        return null
    }
}
