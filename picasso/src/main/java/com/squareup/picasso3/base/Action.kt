package com.squareup.picasso3.base

import com.squareup.picasso3.Picasso
import com.squareup.picasso3.Request

internal abstract class Action(
  val picasso: Picasso, val request: Request
) {
  var willReplay = false
  var cancelled = false

  abstract fun complete(result: RequestHandler.Result)
  abstract fun error(e: Exception)

  abstract fun getTarget(): Any?

  open fun cancel() {
    cancelled = true
  }

  val tag: Any
    get() = request.tag ?: this
}
