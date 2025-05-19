package com.squareup.picasso3.requestHandler

import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetManager
import com.squareup.picasso3.Initializer
import com.squareup.picasso3.Picasso
import com.squareup.picasso3.Request
import com.squareup.picasso3.base.RequestHandler
import com.squareup.picasso3.utils.BitmapUtils
import okio.source

internal class AssetRequestHandler(private val context: Context) : RequestHandler() {
  private val lock = Any()

  @Volatile
  private var assetManager: AssetManager? = null

  override fun canHandleRequest(data: Request): Boolean {
    val uri = data.uri
    return uri != null && ContentResolver.SCHEME_FILE == uri.scheme && uri.pathSegments.isNotEmpty() && ANDROID_ASSET == uri.pathSegments[0]
  }

  override fun load(
    picasso: Picasso, request: Request, callback: Callback
  ) {
    initializeIfFirstTime()
    var signaledCallback = false
    try {
      assetManager!!.open(getFilePath(request)).source().use { source ->
        val bitmap = BitmapUtils.decodeStream(source, request)
        signaledCallback = true
        callback.onSuccess(Result.Bitmap(bitmap, Picasso.LoadedFrom.DISK))
      }
    } catch (e: Exception) {
      if (!signaledCallback) {
        callback.onError(e)
      }
    }
  }

  @Initializer
  private fun initializeIfFirstTime() {
    if (assetManager == null) {
      synchronized(lock) {
        if (assetManager == null) {
          assetManager = context.assets
        }
      }
    }
  }

  companion object {
    private const val ANDROID_ASSET = "android_asset"
    private const val ASSET_PREFIX_LENGTH =
      "${ContentResolver.SCHEME_FILE}:///$ANDROID_ASSET/".length

    fun getFilePath(request: Request): String {
      val uri = checkNotNull(request.uri)
      return uri.toString().substring(ASSET_PREFIX_LENGTH)
    }
  }
}
