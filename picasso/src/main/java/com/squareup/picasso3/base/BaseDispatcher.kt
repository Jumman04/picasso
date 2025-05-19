package com.squareup.picasso3.base

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.util.Log
import androidx.annotation.CallSuper
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import com.squareup.picasso3.BitmapHunter
import com.squareup.picasso3.PlatformLruCache
import com.squareup.picasso3.enums.MemoryPolicy
import com.squareup.picasso3.enums.NetworkPolicy
import com.squareup.picasso3.interfaces.Dispatcher
import com.squareup.picasso3.requestHandler.NetworkRequestHandler
import com.squareup.picasso3.utils.Utils
import java.util.WeakHashMap

internal abstract class BaseDispatcher internal constructor(
  private val context: Context,
  private val mainThreadHandler: Handler,
  private val cache: PlatformLruCache
) : Dispatcher {
  @get:JvmName("-hunterMap")
  internal val hunterMap = mutableMapOf<String, BitmapHunter>()

  @get:JvmName("-failedActions")
  internal val failedActions = WeakHashMap<Any, Action>()

  @get:JvmName("-pausedActions")
  internal val pausedActions = WeakHashMap<Any, Action>()

  @get:JvmName("-pausedTags")
  internal val pausedTags = mutableSetOf<Any>()

  @get:JvmName("-receiver")
  internal val receiver: NetworkBroadcastReceiver = NetworkBroadcastReceiver(this)

  private val scansNetworkChanges: Boolean =
    Utils.hasPermission(context, Manifest.permission.ACCESS_NETWORK_STATE)

  init {
    receiver.register()
  }

  @CallSuper
  override fun shutdown() {
    // Unregister network broadcast receiver on the main thread.
    mainThreadHandler.post { receiver.unregister() }
  }

  fun performSubmit(action: Action, dismissFailed: Boolean = true) {
    if (action.tag in pausedTags) {
      pausedActions[action.getTarget()] = action
      if (action.picasso.isLoggingEnabled) {
        Utils.log(
          owner = Utils.OWNER_DISPATCHER,
          verb = Utils.VERB_PAUSED,
          logId = action.request.logId(),
          extras = "because tag '${action.tag}' is paused"
        )
      }
      return
    }

    var hunter = hunterMap[action.request.key]
    if (hunter != null) {
      hunter.attach(action)
      return
    }

    if (isShutdown()) {
      if (action.picasso.isLoggingEnabled) {
        Utils.log(
          owner = Utils.OWNER_DISPATCHER,
          verb = Utils.VERB__,
          logId = action.request.logId(),
          extras = "because shut down"
        )
      }
      return
    }

    hunter = BitmapHunter.Companion.forRequest(action.picasso, this, cache, action)
    dispatchSubmit(hunter)
    hunterMap[action.request.key] = hunter
    if (dismissFailed) {
      failedActions.remove(action.getTarget())
    }

    if (action.picasso.isLoggingEnabled) {
      Utils.log(
        owner = Utils.OWNER_DISPATCHER,
        verb = Utils.VERB_ENQUEUED,
        logId = action.request.logId()
      )
    }
  }

  fun performCancel(action: Action) {
    val key = action.request.key
    val hunter = hunterMap[key]
    if (hunter != null) {
      hunter.detach(action)
      if (hunter.cancel()) {
        hunterMap.remove(key)
        if (action.picasso.isLoggingEnabled) {
          Utils.log(Utils.OWNER_DISPATCHER, Utils.VERB_CANCELED, action.request.logId())
        }
      }
    }

    if (action.tag in pausedTags) {
      pausedActions.remove(action.getTarget())
      if (action.picasso.isLoggingEnabled) {
        Utils.log(
          owner = Utils.OWNER_DISPATCHER,
          verb = Utils.VERB_CANCELED,
          logId = action.request.logId(),
          extras = "because paused request got canceled"
        )
      }
    }

    val remove = failedActions.remove(action.getTarget())
    if (remove != null && remove.picasso.isLoggingEnabled) {
      Utils.log(
        Utils.OWNER_DISPATCHER,
        Utils.VERB_CANCELED,
        remove.request.logId(),
        "from replaying"
      )
    }
  }

  fun performPauseTag(tag: Any) {
    // Trying to pause a tag that is already paused.
    if (!pausedTags.add(tag)) {
      return
    }

    // Go through all active hunters and detach/pause the requests
    // that have the paused tag.
    val iterator = hunterMap.values.iterator()
    while (iterator.hasNext()) {
      val hunter = iterator.next()
      val loggingEnabled = hunter.picasso.isLoggingEnabled

      val single = hunter.action
      val joined = hunter.actions
      val hasMultiple = !joined.isNullOrEmpty()

      // Hunter has no requests, bail early.
      if (single == null && !hasMultiple) {
        continue
      }

      if (single != null && single.tag == tag) {
        hunter.detach(single)
        pausedActions[single.getTarget()] = single
        if (loggingEnabled) {
          Utils.log(
            owner = Utils.OWNER_DISPATCHER,
            verb = Utils.VERB_PAUSED,
            logId = single.request.logId(),
            extras = "because tag '$tag' was paused"
          )
        }
      }

      if (joined != null) {
        for (i in joined.indices.reversed()) {
          val action = joined[i]
          if (action.tag != tag) {
            continue
          }
          hunter.detach(action)
          pausedActions[action.getTarget()] = action
          if (loggingEnabled) {
            Utils.log(
              owner = Utils.OWNER_DISPATCHER,
              verb = Utils.VERB_PAUSED,
              logId = action.request.logId(),
              extras = "because tag '$tag' was paused"
            )
          }
        }
      }

      // Check if the hunter can be cancelled in case all its requests
      // had the tag being paused here.
      if (hunter.cancel()) {
        iterator.remove()
        if (loggingEnabled) {
          Utils.log(
            owner = Utils.OWNER_DISPATCHER,
            verb = Utils.VERB_CANCELED,
            logId = Utils.getLogIdsForHunter(hunter),
            extras = "all actions paused"
          )
        }
      }
    }
  }

  fun performResumeTag(tag: Any) {
    // Trying to resume a tag that is not paused.
    if (!pausedTags.remove(tag)) {
      return
    }

    val batch = mutableListOf<Action>()
    val iterator = pausedActions.values.iterator()
    while (iterator.hasNext()) {
      val action = iterator.next()
      if (action.tag == tag) {
        batch += action
        iterator.remove()
      }
    }

    if (batch.isNotEmpty()) {
      dispatchBatchResumeMain(batch)
    }
  }

  @SuppressLint("MissingPermission")
  fun performRetry(hunter: BitmapHunter) {
    if (hunter.isCancelled) return

    if (isShutdown()) {
      performError(hunter)
      return
    }

    var isConnected = false
    if (scansNetworkChanges) {
      val connectivityManager =
        ContextCompat.getSystemService(context, ConnectivityManager::class.java)
      if (connectivityManager != null) {
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        isConnected =
          capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true && capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_VALIDATED
          )

      }
    }

    if (hunter.shouldRetry(isConnected)) {
      if (hunter.picasso.isLoggingEnabled) {
        Utils.log(
          owner = Utils.OWNER_DISPATCHER,
          verb = Utils.VERB_RETRYING,
          logId = Utils.getLogIdsForHunter(hunter)
        )
      }
      if (hunter.exception is NetworkRequestHandler.ContentLengthException) {
        hunter.data = hunter.data.newBuilder().networkPolicy(NetworkPolicy.NO_CACHE).build()
      }
      dispatchSubmit(hunter)
    } else {
      performError(hunter)
      // Mark for replay only if we observe network info changes and support replay.
      if (scansNetworkChanges && hunter.supportsReplay()) {
        markForReplay(hunter)
      }
    }
  }

  fun performComplete(hunter: BitmapHunter) {
    if (MemoryPolicy.Companion.shouldWriteToMemoryCache(hunter.data.memoryPolicy)) {
      val result = hunter.result
      if (result != null) {
        if (result is RequestHandler.Result.Bitmap) {
          val bitmap = result.bitmap
          cache[hunter.key] = bitmap
        }
      }
    }
    hunterMap.remove(hunter.key)
    deliver(hunter)
  }

  fun performError(hunter: BitmapHunter) {
    hunterMap.remove(hunter.key)
    deliver(hunter)
  }

  fun performNetworkStateChange(isConnected: Boolean) {
    // Intentionally check only if isConnected() here before we flush out failed actions.
    if (isConnected) {
      flushFailedActions()
    }
  }

  @MainThread
  fun performCompleteMain(hunter: BitmapHunter) {
    hunter.picasso.complete(hunter)
  }

  @MainThread
  fun performBatchResumeMain(batch: List<Action>) {
    for (i in batch.indices) {
      val action = batch[i]
      action.picasso.resumeAction(action)
    }
  }

  private fun flushFailedActions() {
    if (failedActions.isNotEmpty()) {
      val iterator = failedActions.values.iterator()
      while (iterator.hasNext()) {
        val action = iterator.next()
        iterator.remove()
        if (action.picasso.isLoggingEnabled) {
          Utils.log(
            owner = Utils.OWNER_DISPATCHER,
            verb = Utils.VERB_REPLAYING,
            logId = action.request.logId()
          )
        }
        performSubmit(action, false)
      }
    }
  }

  private fun markForReplay(hunter: BitmapHunter) {
    hunter.action?.let { markForReplay(it) }
    hunter.actions?.forEach { markForReplay(it) }
  }

  private fun markForReplay(action: Action) {
    val target = action.getTarget()
    action.willReplay = true
    failedActions[target] = action
  }

  private fun deliver(hunter: BitmapHunter) {
    if (hunter.isCancelled) {
      return
    }
    val result = hunter.result
    if (result != null) {
      if (result is RequestHandler.Result.Bitmap) {
        val bitmap = result.bitmap
        bitmap.prepareToDraw()
      }
    }

    dispatchCompleteMain(hunter)
    logDelivery(hunter)
  }

  private fun logDelivery(bitmapHunter: BitmapHunter) {
    val picasso = bitmapHunter.picasso
    if (picasso.isLoggingEnabled) {
      Utils.log(
        owner = Utils.OWNER_DISPATCHER,
        verb = Utils.VERB_DELIVERED,
        logId = Utils.getLogIdsForHunter(bitmapHunter)
      )
    }
  }

  internal class NetworkBroadcastReceiver(private val dispatcher: BaseDispatcher) {
    private val connectivityManager: ConnectivityManager? =
      ContextCompat.getSystemService(dispatcher.context, ConnectivityManager::class.java)

    private val callback = object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        dispatcher.dispatchNetworkStateChange(true)
      }

      override fun onLost(network: Network) {
        dispatcher.dispatchNetworkStateChange(false)
      }
    }

    @SuppressLint("MissingPermission")
    fun register() {
      if (Utils.hasPermission(dispatcher.context, Manifest.permission.ACCESS_NETWORK_STATE)) {
        val request = NetworkRequest.Builder().build()
        connectivityManager?.registerNetworkCallback(request, callback)
      } else {
        Log.w("Network", "Skipping network callback: ACCESS_NETWORK_STATE permission not granted")
      }
    }


    fun unregister() {
      connectivityManager?.unregisterNetworkCallback(callback)
    }
  }

}
