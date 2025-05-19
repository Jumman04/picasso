package com.squareup.picasso3.interfaces

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import com.squareup.picasso3.Picasso

/**
 * Represents an arbitrary listener for image loading.
 *
 * Objects implementing this class **must** have a working implementation of
 * [Object.equals] and [Object.hashCode] for proper storage internally.
 * Instances of this interface will also be compared to determine if view recycling is occurring.
 * It is recommended that you add this interface directly on to a custom view type when using in an
 * adapter to ensure correct recycling behavior.
 */
interface BitmapTarget {
  /**
   * Callback when an image has been successfully loaded.
   *
   * **Note:** You must not recycle the bitmap.
   */
  fun onBitmapLoaded(
    bitmap: Bitmap, from: Picasso.LoadedFrom
  )

  /**
   * Callback indicating the image could not be successfully loaded.
   *
   * **Note:** The passed [android.graphics.drawable.Drawable] may be `null` if none has been
   * specified via [com.squareup.picasso3.RequestCreator.error] or [com.squareup.picasso3.RequestCreator.error].
   */
  fun onBitmapFailed(
    e: Exception, errorDrawable: Drawable?
  )

  /**
   * Callback invoked right before your request is submitted.
   *
   *
   * **Note:** The passed [Drawable] may be `null` if none has been
   * specified via [com.squareup.picasso3.RequestCreator.placeholder] or [com.squareup.picasso3.RequestCreator.placeholder].
   */
  fun onPrepareLoad(placeHolderDrawable: Drawable?)
}
