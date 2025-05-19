/*
 * Copyright (C) 2014 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.picasso3.requestHandler

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import android.provider.MediaStore.Video
import android.util.Size
import com.squareup.picasso3.Picasso
import com.squareup.picasso3.Picasso.LoadedFrom
import com.squareup.picasso3.Request
import com.squareup.picasso3.utils.BitmapUtils.calculateInSampleSize
import com.squareup.picasso3.utils.BitmapUtils.decodeStream
import java.io.IOException

internal class MediaStoreRequestHandler(context: Context) : ContentStreamRequestHandler(context) {
  override fun canHandleRequest(data: Request): Boolean {
    val uri = data.uri
    return uri != null && ContentResolver.SCHEME_CONTENT == uri.scheme && MediaStore.AUTHORITY == uri.authority
  }

  override fun load(picasso: Picasso, request: Request, callback: Callback) {
    var signaledCallback = false
    try {
      val contentResolver = context.contentResolver
      val requestUri: Uri = checkNotNull(request.uri) { "request.uri == null" }
      val orientation = getExifOrientation(requestUri)

      // Try thumbnail generation when size requested
      if (request.hasSize()) {
        val width = request.targetWidth
        val height = request.targetHeight
        val thumb = loadThumbnail(contentResolver, requestUri, width, height, request)
        if (thumb != null) {
          signaledCallback = true
          callback.onSuccess(Result.Bitmap(thumb, LoadedFrom.DISK, orientation))
          return
        }
      }

      // Fallback: full decode with down sampling
      val source = getSource(requestUri)
      val bitmap = decodeStream(source, request)
      signaledCallback = true
      callback.onSuccess(Result.Bitmap(bitmap, LoadedFrom.DISK, orientation))
    } catch (e: Exception) {
      if (!signaledCallback) {
        callback.onError(e)
      }
    }
  }

  @Throws(IOException::class)
  private fun loadThumbnail(
    resolver: ContentResolver, uri: Uri, width: Int, height: Int, request: Request
  ): Bitmap? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      // Modern thumbnail API for images and videos
      resolver.loadThumbnail(uri, Size(width, height), CancellationSignal())
    } else {
      // Legacy pre-Q thumbnail via MediaStore
      val id = ContentUris.parseId(uri)
      val mime = resolver.getType(uri) ?: return null
      val isVideo = mime.startsWith("video/")
      val kind = getPicassoKind(width, height).androidKind

      // Prepare options for down sampling
      val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
      }
      calculateInSampleSize(
        request.targetWidth, request.targetHeight,/* reqWidth */
        width,/* reqHeight */
        height, options, request
      )
      options.inJustDecodeBounds = false

      @Suppress("DEPRECATION") return if (isVideo) {
        // Video thumbnail
        Video.Thumbnails.getThumbnail(resolver, id, kind, options)
      } else {
        // Image thumbnail
        MediaStore.Images.Thumbnails.getThumbnail(resolver, id, kind, options)
      }
    }
  }

  /**
   * Choose a thumbnail kind based on requested target dimensions.
   */
  private fun getPicassoKind(targetWidth: Int, targetHeight: Int): PicassoKind {
    return when {
      targetWidth <= PicassoKind.MICRO.width && targetHeight <= PicassoKind.MICRO.height -> PicassoKind.MICRO
      targetWidth <= PicassoKind.MINI.width && targetHeight <= PicassoKind.MINI.height -> PicassoKind.MINI
      else -> PicassoKind.FULL
    }
  }

  @Suppress("DEPRECATION")
  private enum class PicassoKind(val androidKind: Int, val width: Int, val height: Int) {
    MICRO(MediaStore.Images.Thumbnails.MICRO_KIND, 96, 96),
    MINI(MediaStore.Images.Thumbnails.MINI_KIND, 512, 384),
    FULL(MediaStore.Images.Thumbnails.FULL_SCREEN_KIND, -1, -1)
  }
}
