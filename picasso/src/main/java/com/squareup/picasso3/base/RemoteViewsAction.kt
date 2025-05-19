package com.squareup.picasso3.base

import android.app.Notification
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.squareup.picasso3.Picasso
import com.squareup.picasso3.Request
import com.squareup.picasso3.interfaces.Callback

internal abstract class RemoteViewsAction(
  picasso: Picasso,
  data: Request,
  @DrawableRes val errorResId: Int,
  val target: RemoteViewsTarget,
  var callback: Callback?
) : Action(picasso, data) {
  override fun complete(result: RequestHandler.Result) {
    if (result is RequestHandler.Result.Bitmap) {
      target.remoteViews.setImageViewBitmap(target.viewId, result.bitmap)
      update()
      callback?.onSuccess()
    }
  }

  override fun cancel() {
    super.cancel()
    callback = null
  }

  override fun error(e: Exception) {
    if (errorResId != 0) {
      setImageResource(errorResId)
    }
    callback?.onError(e)
  }

  fun setImageResource(resId: Int) {
    target.remoteViews.setImageViewResource(target.viewId, resId)
    update()
  }

  abstract fun update()

  internal class RemoteViewsTarget(
    val remoteViews: RemoteViews, val viewId: Int
  ) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other == null || javaClass != other.javaClass) return false
      val remoteViewsTarget = other as RemoteViewsTarget
      return viewId == remoteViewsTarget.viewId && remoteViews == remoteViewsTarget.remoteViews
    }

    override fun hashCode(): Int {
      return 31 * remoteViews.hashCode() + viewId
    }
  }

  internal class AppWidgetAction(
    picasso: Picasso,
    data: Request,
    @DrawableRes errorResId: Int,
    target: RemoteViewsTarget,
    private val appWidgetIds: IntArray,
    callback: Callback?
  ) : RemoteViewsAction(picasso, data, errorResId, target, callback) {
    override fun update() {
      val manager = AppWidgetManager.getInstance(picasso.context)
      manager.updateAppWidget(appWidgetIds, target.remoteViews)
    }

    override fun getTarget(): Any {
      return target
    }
  }

  internal class NotificationAction(
    picasso: Picasso,
    data: Request,
    @DrawableRes errorResId: Int,
    target: RemoteViewsTarget,
    private val notificationId: Int,
    private val notification: Notification,
    private val notificationTag: String?,
    callback: Callback?
  ) : RemoteViewsAction(picasso, data, errorResId, target, callback) {
    override fun update() {
      val manager = ContextCompat.getSystemService(
        picasso.context, NotificationManager::class.java
      )
      manager?.notify(notificationTag, notificationId, notification)
    }

    override fun getTarget(): Any {
      return target
    }
  }
}
