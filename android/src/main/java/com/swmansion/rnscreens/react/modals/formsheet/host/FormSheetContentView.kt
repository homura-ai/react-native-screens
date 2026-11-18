package com.swmansion.rnscreens.react.modals.formsheet.host

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.facebook.react.bridge.UIManager
import com.facebook.react.bridge.UIManagerListener
import com.facebook.react.common.annotations.UnstableReactNativeAPI
import com.facebook.react.config.ReactFeatureFlags
import com.facebook.react.uimanager.JSPointerDispatcher
import com.facebook.react.uimanager.JSTouchDispatcher
import com.facebook.react.uimanager.RootView
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.common.UIManagerType
import com.facebook.react.uimanager.events.EventDispatcher
import com.facebook.react.views.view.ReactViewGroup
import com.swmansion.rnscreens.core.modals.formsheet.interfaces.FormSheetContentSizeChangeDelegate
import com.swmansion.rnscreens.core.modals.formsheet.interfaces.FormSheetContentSizeChangeProvider
import com.swmansion.rnscreens.helpers.getFabricUIManagerNotNull
import java.lang.ref.WeakReference

@OptIn(UnstableReactNativeAPI::class)
@SuppressLint("ViewConstructor")
class FormSheetContentView(
    context: Context,
    private val onSizeChangedCallback: (width: Int, height: Int) -> Unit,
) : ReactViewGroup(context),
    RootView,
    UIManagerListener,
    ViewTreeObserver.OnPreDrawListener {
    internal var contentSizeChangeDelegate: FormSheetContentSizeChangeDelegate? = null

    private val themedReactContext: ThemedReactContext
        get() = context as ThemedReactContext
    private val jsTouchDispatcher = JSTouchDispatcher(this)
    private var jsPointerDispatcher: JSPointerDispatcher? = null

    private val eventDispatcher: EventDispatcher?
        get() = UIManagerHelper.getEventDispatcher(themedReactContext, UIManagerType.FABRIC)

    private var isWaitingForFabricMount = false

    private var nestedScrollingChild: WeakReference<View>? = null

    init {
        if (ReactFeatureFlags.dispatchPointerEvents) {
            jsPointerDispatcher = JSPointerDispatcher(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(this)
        themedReactContext.let { themedContext ->
            UIManagerHelper
                .getFabricUIManagerNotNull(themedContext)
                .addUIManagerEventListener(this)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewTreeObserver.removeOnPreDrawListener(this)
        themedReactContext.let { themedContext ->
            UIManagerHelper
                .getFabricUIManagerNotNull(themedContext)
                .removeUIManagerEventListener(this)
        }
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        (child as? FormSheetContentSizeChangeProvider)?.setContentSizeChangeDelegate(contentSizeChangeDelegate)
    }

    override fun onViewRemoved(child: View) {
        super.onViewRemoved(child)
        (child as? FormSheetContentSizeChangeProvider)?.setContentSizeChangeDelegate(null)
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) {
            isWaitingForFabricMount = true
            onSizeChangedCallback(w, h)
        }
    }

    override fun onPreDraw(): Boolean {
        // If we're waiting for Fabric to update the recalculated layout for views, we prevent drawing.
        // This cancels the current drawing pass to prevent drawing the screen with stale React children frames.
        return !isWaitingForFabricMount
    }

    override fun didDispatchMountItems(uiManager: UIManager) {
        isWaitingForFabricMount = false
        // BottomSheetBehavior picks the view its drags hand off to only while laying out the sheet, and
        // React views swallow requestLayout, so lay the sheet out again when that view changes.
        val scrollingChild = findNestedScrollingChild(this)
        if (scrollingChild !== nestedScrollingChild?.get()) {
            nestedScrollingChild = scrollingChild?.let(::WeakReference)
            (parent as? View)?.requestLayout()
        }
    }

    /** The same depth-first search BottomSheetBehavior runs for its scrolling child. */
    private fun findNestedScrollingChild(view: View): View? {
        if (view.visibility != VISIBLE) return null
        if (view.isNestedScrollingEnabled) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findNestedScrollingChild(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    override fun willDispatchViewUpdates(uiManager: UIManager) = Unit

    override fun willMountItems(uiManager: UIManager) = Unit

    override fun didMountItems(uiManager: UIManager) = Unit

    override fun didScheduleMountItems(uiManager: UIManager) = Unit

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        eventDispatcher?.let { eventDispatcher ->
            jsTouchDispatcher.handleTouchEvent(event, eventDispatcher, themedReactContext)
            jsPointerDispatcher?.handleMotionEvent(event, eventDispatcher, true)
        }
        return super.onInterceptTouchEvent(event)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        eventDispatcher?.let { eventDispatcher ->
            jsTouchDispatcher.handleTouchEvent(event, eventDispatcher, themedReactContext)
            jsPointerDispatcher?.handleMotionEvent(event, eventDispatcher, false)
        }
        super.onTouchEvent(event)
        // In case when there is no children interested in handling touch event, we return true from
        // the root view in order to receive subsequent events related to that gesture
        return true
    }

    override fun onInterceptHoverEvent(event: MotionEvent): Boolean {
        eventDispatcher?.let { jsPointerDispatcher?.handleMotionEvent(event, it, true) }
        return super.onInterceptHoverEvent(event)
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        eventDispatcher?.let { jsPointerDispatcher?.handleMotionEvent(event, it, false) }
        return super.onHoverEvent(event)
    }

    override fun onChildStartedNativeGesture(
        childView: View?,
        ev: MotionEvent,
    ) {
        eventDispatcher?.let { eventDispatcher ->
            jsTouchDispatcher.onChildStartedNativeGesture(ev, eventDispatcher, themedReactContext)
            jsPointerDispatcher?.onChildStartedNativeGesture(childView, ev, eventDispatcher)
        }
    }

    override fun onChildEndedNativeGesture(
        childView: View,
        ev: MotionEvent,
    ) {
        eventDispatcher?.let { jsTouchDispatcher.onChildEndedNativeGesture(ev, it) }
        jsPointerDispatcher?.onChildEndedNativeGesture()
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // No-op - override in order to still receive events to onInterceptTouchEvent
        // even when some other view disallow that
    }

    override fun handleException(t: Throwable) {
        themedReactContext.reactApplicationContext.handleException(RuntimeException(t))
    }
}
