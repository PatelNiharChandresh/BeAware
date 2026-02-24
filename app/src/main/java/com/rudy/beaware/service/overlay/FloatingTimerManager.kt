package com.rudy.beaware.service.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.compose.runtime.mutableLongStateOf
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

    private val _elapsedSeconds = mutableLongStateOf(0L)

    private var containerView: FrameLayout? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var isOnRightEdge: Boolean = false
    private var ratioY: Float = 0.046f
    private var pillWidth: Int = 0
    private var pillHeight: Int = 0
    private var snapAnimator: ValueAnimator? = null

    private fun getScreenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val metrics = context.resources.displayMetrics
            Pair(metrics.widthPixels, metrics.heightPixels)
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val (screenWidth, screenHeight) = getScreenSize()

        val absX = if (pillWidth > 0) {
            if (isOnRightEdge) (screenWidth - pillWidth) else 0
        } else {
            0
        }

        val absY = if (pillHeight > 0) {
            (ratioY * (screenHeight - pillHeight)).toInt()
        } else {
            100
        }

        val (safeX, safeY) = clampPosition(absX, absY)

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = safeX
            y = safeY
        }
    }

    private fun clampPosition(x: Int, y: Int): Pair<Int, Int> {
        if (pillWidth == 0 || pillHeight == 0) {
            return Pair(x, y)
        }

        val (screenWidth, screenHeight) = getScreenSize()

        val clampedX = x.coerceIn(0, screenWidth - pillWidth)
        val clampedY = y.coerceIn(0, screenHeight - pillHeight)

        return Pair(clampedX, clampedY)
    }

    private fun snapToEdge() {
        val lp = layoutParams ?: return
        val view = containerView ?: return
        if (pillWidth == 0) return

        val (screenWidth, screenHeight) = getScreenSize()
        val currentX = lp.x
        val currentY = lp.y

        val pillCenterX = currentX + pillWidth / 2
        val goRight = pillCenterX > screenWidth / 2

        val targetX = if (goRight) (screenWidth - pillWidth) else 0

        isOnRightEdge = goRight
        ratioY = if (screenHeight - pillHeight > 0) {
            currentY.toFloat() / (screenHeight - pillHeight)
        } else {
            0f
        }
        ratioY = ratioY.coerceIn(0f, 1f)

        Timber.d("snapToEdge: currentX=%d, targetX=%d, right=%s, ratioY=%.3f",
            currentX, targetX, goRight, ratioY)

        snapAnimator?.cancel()

        snapAnimator = ValueAnimator.ofInt(currentX, targetX).apply {
            duration = 250
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                lp.x = animator.animatedValue as Int
                try {
                    windowManager.updateViewLayout(view, lp)
                } catch (e: IllegalArgumentException) {
                    Timber.d("snapToEdge: view removed during animation, cancelling")
                    cancel()
                }
            }
            start()
        }

        Timber.d("snapToEdge: animation started")
    }

    fun show() {
        Timber.d("show: alreadyVisible=%s", containerView != null)
        if (containerView != null) return

        _elapsedSeconds.longValue = 0L

        layoutParams = createLayoutParams()

        val owner = OverlayLifecycleOwner()
        owner.onCreate()
        Timber.d("show: OverlayLifecycleOwner created and CREATED")

        val composeView = ComposeView(context)
        composeView.setContent {
            TimerPill(
                elapsedSeconds = _elapsedSeconds.longValue,
                onDrag = { dx, dy ->
                    snapAnimator?.cancel()

                    layoutParams?.let { lp ->
                        lp.x += dx.toInt()
                        lp.y += dy.toInt()

                        val (clampedX, clampedY) = clampPosition(lp.x, lp.y)
                        lp.x = clampedX
                        lp.y = clampedY

                        containerView?.let { view ->
                            windowManager.updateViewLayout(view, lp)
                        }

                        Timber.d("onDrag: dx=%.0f, dy=%.0f, pos=(%d, %d)", dx, dy, clampedX, clampedY)
                    }
                },
                onDragEnd = {
                    snapToEdge()
                }
            )
        }

        val container = FrameLayout(context).apply {
            addView(composeView)
        }

        container.setViewTreeLifecycleOwner(owner)
        container.setViewTreeSavedStateRegistryOwner(owner)

        windowManager.addView(container, layoutParams)

        container.post {
            pillWidth = container.width
            pillHeight = container.height
            Timber.d("show: pill measured — %dx%d", pillWidth, pillHeight)
        }

        owner.onResume()
        Timber.d("show: overlay added to WindowManager, lifecycle RESUMED")

        containerView = container
        lifecycleOwner = owner
    }

    fun updateTimer(seconds: Long) {
        Timber.d("updateTimer: seconds=%d", seconds)
        _elapsedSeconds.longValue = seconds
    }

    fun hide() {
        Timber.d("hide: containerView=%s", containerView != null)
        snapAnimator?.cancel()
        snapAnimator = null
        containerView?.let { view ->
            windowManager.removeView(view)
            Timber.d("hide: overlay removed from WindowManager")
        }
        lifecycleOwner?.onDestroy()
        containerView = null
        lifecycleOwner = null
        layoutParams = null
        Timber.d("hide: cleanup complete, lifecycle DESTROYED")
    }

    fun onConfigurationChanged() {
        snapAnimator?.cancel()

        val view = containerView ?: return
        val lp = layoutParams ?: return
        if (pillWidth == 0 || pillHeight == 0) return

        val (screenWidth, screenHeight) = getScreenSize()

        val newX = if (isOnRightEdge) (screenWidth - pillWidth) else 0
        val newY = (ratioY * (screenHeight - pillHeight)).toInt()
            .coerceIn(0, screenHeight - pillHeight)

        lp.x = newX
        lp.y = newY

        try {
            windowManager.updateViewLayout(view, lp)
        } catch (e: IllegalArgumentException) {
            Timber.d("onConfigurationChanged: view not attached, skipping layout update")
            return
        }

        Timber.d("onConfigurationChanged: right=%s, ratioY=%.3f, newPos=(%d, %d)",
            isOnRightEdge, ratioY, newX, newY)

        view.post {
            pillWidth = view.width
            pillHeight = view.height
            Timber.d("onConfigurationChanged: pill re-measured — %dx%d", pillWidth, pillHeight)
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
