package com.squareup.picasso3;

import static android.graphics.Bitmap.Config.ALPHA_8;
import static com.squareup.picasso3.Picasso.LoadedFrom.DISK;

import android.content.Context;
import android.graphics.Bitmap;

import com.squareup.picasso3.base.RequestHandler;
import com.squareup.picasso3.interfaces.Dispatcher;
import com.squareup.picasso3.requestHandler.AssetRequestHandler;
import com.squareup.picasso3.requestHandler.ResourceDrawableRequestHandler;

import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;

public class JavaConsumerIdeCheckTest {
  @Mock
  Context context;

  @Test
  @Ignore("Quick IDE check for compile-time access to Kotlin internal methods from Java callers")
  public void name() {
    Picasso picasso = new Picasso.Builder(context).build();
    picasso.setLoggingEnabled(true);

    RequestCreator requestCreator = picasso.load("");
    requestCreator.fit();

    AssetRequestHandler assetRequestHandler = new AssetRequestHandler(context);
    assetRequestHandler.getRetryCount();

    Request.Builder requestBuilder = new Request.Builder(0);
    requestBuilder.getRotationDegrees();

    Request request = requestBuilder.build();

    MatrixTransformation matrixTransformation = new MatrixTransformation(request);

    Bitmap bitmap = Bitmap.createBitmap(0, 0, ALPHA_8);
    RequestHandler.Result.Bitmap transform = matrixTransformation.transform(new RequestHandler.Result.Bitmap(bitmap, DISK, 0));
    Picasso.LoadedFrom loadedFrom = transform.getLoadedFrom();
    Picasso.RequestTransformer requestTransformer = request1 -> request1;

    Dispatcher.Companion companion1 = Dispatcher.Companion;
    MatrixTransformation.Companion companion = MatrixTransformation.Companion;
    Picasso.Companion companion2 = Picasso.Companion;
    ResourceDrawableRequestHandler.Companion companion3 = ResourceDrawableRequestHandler.Companion;
  }
}
