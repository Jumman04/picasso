package com.squareup.picasso3.interfaces

interface Callback {
  fun onSuccess()
  fun onError(t: Throwable)
  open class EmptyCallback : Callback {
    override fun onSuccess() = Unit
    override fun onError(t: Throwable) = Unit
  }
}
