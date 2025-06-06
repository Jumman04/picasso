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

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.NetworkCapabilities;
import android.telephony.TelephonyManager;

import androidx.core.content.ContextCompat;

import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The default {@link java.util.concurrent.ExecutorService} used for new {@link Picasso} instances.
 * <p>
 * Exists as a custom type so that we can differentiate the use of defaults versus a user-supplied
 * instance.
 */
class PicassoExecutorService extends ThreadPoolExecutor {
    private static final int DEFAULT_THREAD_COUNT = 3;

    PicassoExecutorService() {
        super(DEFAULT_THREAD_COUNT, DEFAULT_THREAD_COUNT, 0, TimeUnit.MILLISECONDS, new PriorityBlockingQueue<>(), new Utils.PicassoThreadFactory());
    }

    void adjustThreadCount(Context context, NetworkCapabilities capabilities) {

        TelephonyManager tm = ContextCompat.getSystemService(context, TelephonyManager.class);
        if (capabilities == null || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            setThreadCount(DEFAULT_THREAD_COUNT);
            return;
        }

        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE)) {
            setThreadCount(4);
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {

            if (tm == null) {
                setThreadCount(DEFAULT_THREAD_COUNT);
                return;
            }

            @SuppressLint("MissingPermission") int networkType = tm.getDataNetworkType(); // returns subtype equivalent

            switch (networkType) {
                case TelephonyManager.NETWORK_TYPE_LTE:     // 4G
                case TelephonyManager.NETWORK_TYPE_HSPAP:
                case TelephonyManager.NETWORK_TYPE_EHRPD:
                    setThreadCount(3);
                    break;

                case TelephonyManager.NETWORK_TYPE_UMTS:     // 3G
                case TelephonyManager.NETWORK_TYPE_CDMA:
                case TelephonyManager.NETWORK_TYPE_EVDO_0:
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                case TelephonyManager.NETWORK_TYPE_EVDO_B:
                    setThreadCount(2);
                    break;

                case TelephonyManager.NETWORK_TYPE_GPRS:     // 2G
                case TelephonyManager.NETWORK_TYPE_EDGE:
                    setThreadCount(1);
                    break;

                default:
                    setThreadCount(DEFAULT_THREAD_COUNT);
                    break;
            }
        } else {
            setThreadCount(DEFAULT_THREAD_COUNT);
        }
    }


    private void setThreadCount(int threadCount) {
        setCorePoolSize(threadCount);
        setMaximumPoolSize(threadCount);
    }

    @Override
    public Future<?> submit(Runnable task) {
        PicassoFutureTask picassoTask = new PicassoFutureTask((BitmapHunter) task);
        execute(picassoTask);
        return picassoTask;
    }

    private static final class PicassoFutureTask extends FutureTask<BitmapHunter> implements Comparable<PicassoFutureTask> {
        private final BitmapHunter hunter;

        PicassoFutureTask(BitmapHunter hunter) {
            super(hunter, null);
            this.hunter = hunter;
        }

        @Override
        public int compareTo(PicassoFutureTask other) {
            Picasso.Priority p1 = hunter.getPriority();
            Picasso.Priority p2 = other.hunter.getPriority();

            // High-priority requests are "lesser" so they are sorted to the front.
            // Equal priorities are sorted by sequence number to provide FIFO ordering.
            return (p1 == p2 ? hunter.sequence - other.hunter.sequence : p2.ordinal() - p1.ordinal());
        }
    }
}
