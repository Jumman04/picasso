/*
 * Copyright (C) 2022 Square, Inc.
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
package com.example.picasso.activity

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.ViewAnimator
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.example.picasso.PicassoInitializer
import com.example.picasso.R
import com.squareup.picasso3.interfaces.Callback.EmptyCallback

class SampleGalleryActivity : PicassoSampleActivity() {
  private lateinit var imageView: ImageView
  lateinit var animator: ViewAnimator

  private var image: String? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.sample_gallery_activity)

    animator = findViewById(R.id.animator)
    imageView = findViewById(R.id.image)

    val button = findViewById<Button>(R.id.go)
    button.setOnClickListener {
      resultLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    button.performClick()

    if (savedInstanceState != null) {
      image = savedInstanceState.getString(KEY_IMAGE)
      if (image != null) {
        loadImage()
      }
    }
  }

  private val resultLauncher =
    registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        image = uri.toString()
        loadImage()
      } else imageView.setImageDrawable(null)
    }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putString(KEY_IMAGE, image)
  }

  private fun loadImage() {
    // Index 1 is the progress bar. Show it while we're loading the image.
    animator.displayedChild = 1

    PicassoInitializer.Companion.get().load(image).fit().centerInside().into(
      imageView, object : EmptyCallback() {
        override fun onSuccess() {
          // Index 0 is the image view.
          animator.displayedChild = 0
        }
      })
  }

  companion object {
    private const val KEY_IMAGE = "com.example.picasso:image"
  }
}
