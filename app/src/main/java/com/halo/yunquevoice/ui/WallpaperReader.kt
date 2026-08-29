package com.halo.yunquevoice.ui

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable

/** 多通道读取系统壁纸，兼容 HyperOS/MIUI 等不同实现。 */
object WallpaperReader {

    fun load(context: Context): Bitmap? {
        // 1. WallpaperManager.getDrawable()
        runCatching {
            val drawable = WallpaperManager.getInstance(context).drawable
            if (drawable is BitmapDrawable) return drawable.bitmap
        }

        // 2. 直接读取壁纸文件
        runCatching {
            val pfd = WallpaperManager.getInstance(context)
                .getWallpaperFile(WallpaperManager.FLAG_SYSTEM)
            if (pfd != null) {
                val bitmap = BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)
                if (bitmap != null) return bitmap
            }
        }

        // 3. 尝试锁屏壁纸
        runCatching {
            val pfd = WallpaperManager.getInstance(context)
                .getWallpaperFile(WallpaperManager.FLAG_LOCK)
            if (pfd != null) {
                val bitmap = BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)
                if (bitmap != null) return bitmap
            }
        }

        return null
    }
}
