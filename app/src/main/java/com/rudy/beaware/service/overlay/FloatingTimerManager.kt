package com.rudy.beaware.service.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import timber.log.Timber

class FloatingTimerManager(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val _appLabel = mutableStateOf("")
    private val _elapsedSeconds = mutableLongStateOf(0L)

    private var containerView: FrameLayout? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    private fun createLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }
    }

    fun show(appLabel: String) {
        Timber.d("show: appLabel=%s, alreadyVisible=%s", appLabel, containerView != null)
        if (containerView != null) {
            updateLabel(appLabel)
            return
        }

        _appLabel.value = appLabel
        _elapsedSeconds.longValue = 0L

        val params = createLayoutParams()

        val owner = OverlayLifecycleOwner()
        owner.onCreate()
        Timber.d("show: OverlayLifecycleOwner created and CREATED")

        val composeView = ComposeView(context).apply {
            setContent {
                TimerPill(
                    appLabel = _appLabel.value,
                    elapsedSeconds = _elapsedSeconds.longValue
                )
            }
        }

        val container = FrameLayout(context).apply {
            addView(composeView)
        }

        container.setViewTreeLifecycleOwner(owner)
        container.setViewTreeSavedStateRegistryOwner(owner)
        container.setOnTouchListener(createDragTouchListener(params))

        windowManager.addView(container, params)
        owner.onResume()
        Timber.d("show: overlay added to WindowManager, lifecycle RESUMED")

        containerView = container
        lifecycleOwner = owner
    }

    fun updateTimer(seconds: Long) {
        Timber.d("updateTimer: seconds=%d", seconds)
        _elapsedSeconds.longValue = seconds
    }

    fun updateLabel(label: String) {
        Timber.d("updateLabel: label=%s", label)
        _appLabel.value = label
    }

    fun hide() {
        Timber.d("hide: containerView=%s", containerView != null)
        containerView?.let { view ->
            windowManager.removeView(view)
            Timber.d("hide: overlay removed from WindowManager")
        }
        lifecycleOwner?.onDestroy()
        containerView = null
        lifecycleOwner = null
        Timber.d("hide: cleanup complete, lifecycle DESTROYED")
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createDragTouchListener(
        params: WindowManager.LayoutParams
    ): View.OnTouchListener {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        return View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    Timber.d("drag: ACTION_DOWN at (%.0f, %.0f)", event.rawX, event.rawY)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    containerView?.let {
                        windowManager.updateViewLayout(it, params)
                    }
                    true
                }
                else -> false
            }
        }
    }
}

private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun onCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun onResume() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}
