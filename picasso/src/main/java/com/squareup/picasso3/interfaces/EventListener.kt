package com.squareup.picasso3.interfaces

import android.graphics.Bitmap
import java.io.Closeable

interface EventListener : Closeable {
  fun cacheMaxSize(maxSize: Int)
  fun cacheSize(size: Int)
  fun cacheHit()
  fun cacheMiss()
  fun downloadFinished(size: Long)
  fun bitmapDecoded(bitmap: Bitmap)
  fun bitmapTransformed(bitmap: Bitmap)
  override fun close() = Unit
}
