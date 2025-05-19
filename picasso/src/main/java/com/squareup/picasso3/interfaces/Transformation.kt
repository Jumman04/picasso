package com.squareup.picasso3.interfaces

import com.squareup.picasso3.base.RequestHandler

/** Image transformation.  */
interface Transformation {
  /**
   * Transform the source result into a new result. If you create a new bitmap instance, you must
   * call [android.graphics.Bitmap.recycle] on `source`. You may return the original
   * if no transformation is required.
   */
  fun transform(source: RequestHandler.Result.Bitmap): RequestHandler.Result.Bitmap

  /**
   * Returns a unique key for the transformation, used for caching purposes. If the transformation
   * has parameters (e.g. size, scale factor, etc) then these should be part of the key.
   */
  fun key(): String
}
