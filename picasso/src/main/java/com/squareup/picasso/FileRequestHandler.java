/*
 * Copyright (C) 2013 Square, Inc.
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
package com.squareup.picasso;

import static android.content.ContentResolver.SCHEME_FILE;
import static androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL;
import static androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION;
import static com.squareup.picasso.Picasso.LoadedFrom.DISK;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.exifinterface.media.ExifInterface;

import java.io.IOException;

import okio.Okio;
import okio.Source;

class FileRequestHandler extends ContentStreamRequestHandler {

    FileRequestHandler(Context context) {
        super(context);
    }

    static int getFileExifRotation(@NonNull Uri uri) throws IOException {
        ExifInterface exifInterface = new ExifInterface(uri.getPath());
        return exifInterface.getAttributeInt(TAG_ORIENTATION, ORIENTATION_NORMAL);
    }

    @Override
    public boolean canHandleRequest(Request data) {
        return SCHEME_FILE.equals(data.uri.getScheme());
    }

    @Override
    public Result load(Request request, int networkPolicy) throws IOException {
        Source source = Okio.source(getInputStream(request));
        return new Result(null, source, DISK, getFileExifRotation(request.uri));
    }
}
