package com.example.autoclear

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.max
import kotlin.math.roundToInt

// This service is the heart of the app. It watches accessibility events,
// decides whether the user is actually looking at Recents, renders the branded
// overlay, and drives the fallback swipe automation when the launcher does not
// expose its own "Clear all" control.
class RecentsAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    // The watchdog is a second line of defense: if Android misses an exit event
    // we still force-hide the overlay as soon as the active window stops looking
    // like a genuine Recents surface.
    private val overlayWatchdog =
        object : Runnable {
            override fun run() {
                val overlayVisible = overlayView != null
                if (!overlayVisible) {
                    return
                }

                val root = rootInActiveWindow
                if (!clearingInProgress) {
                    if (root == null || isBlockedSurface(root) || isLauncherSurface(root) || !hasExplicitRecentsMarkers(root)) {
                        hideOverlay()
                        return
                    }
                }

                handler.postDelayed(this, OVERLAY_WATCHDOG_MS)
            }
        }

    private lateinit var windowManager: WindowManager
    private lateinit var keyguardManager: KeyguardManager
    private var overlayView: View? = null
    private var overlayTopLabel: TextView? = null
    private var overlayCenterLabel: TextView? = null
    private var overlayBottomLabel: TextView? = null
    private var clearPassCount = 0
    private var clearingInProgress = false
    private var returnHomeAfterClear = false
    // We keep a short "last seen recents" timestamp so the clear pass can finish
    // across tiny launcher transitions without instantly aborting.
    private var lastRecentsSignalUptimeMs = 0L
    // Suppression prevents the overlay from bouncing back during the Home return
    // transition after a clear run.
    private var overlaySuppressedUntilUptimeMs = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WindowManager::class.java)
        keyguardManager = getSystemService(KeyguardManager::class.java)
        serviceInfo = serviceInfo.apply {
            // These flags give us more reliable node ids and multi-window detail,
            // which makes the Recents heuristics easier to tune per launcher.
            flags =
                flags or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) {
            return
        }

        val root = rootInActiveWindow
        // Exit early on any surface we never want to automate, even if SystemUI
        // produces noisy events that look vaguely similar to Recents.
        if (!SettingsRepository.isEnabled(this) || isBlockedSurface(root, event.className?.toString().orEmpty())) {
            abortClearRun()
            hideOverlay()
            return
        }

        if (shouldShowOverlay(event, root)) {
            lastRecentsSignalUptimeMs = SystemClock.uptimeMillis()
            showOverlay()
        } else if (!clearingInProgress) {
            hideOverlay()
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        hideOverlay()
        super.onDestroy()
    }

    private fun showOverlay() {
        if (overlayView != null) {
            restartOverlayWatchdog()
            return
        }

        // The overlay is assembled in code instead of XML because it is small,
        // highly dynamic, and easier to tweak here while testing different devices.
        val topLabel = TextView(this).apply {
            text = "BabaG"
            setTextColor(Color.parseColor("#71F66B"))
            textSize = 10f
            typeface = Typeface.create("monospace", Typeface.BOLD)
            letterSpacing = 0.12f
        }

        val centerLabel = TextView(this).apply {
            text = "CLEAR"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.08f
        }

        val bottomLabel = TextView(this).apply {
            text = "RECENTS"
            setTextColor(Color.parseColor("#D7F2E1"))
            textSize = 9f
            typeface = Typeface.create("monospace", Typeface.BOLD)
            letterSpacing = 0.16f
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(topLabel)
            addView(centerLabel)
            addView(bottomLabel)
        }

        val innerOrb = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(88), dp(88), Gravity.CENTER)
            background =
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#F10B0F13"))
                    setStroke(dp(1), Color.parseColor("#2C71F66B"))
                }
            addView(content, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ))
        }

        val container = FrameLayout(this).apply {
            setPadding(0, dp(10), dp(10), dp(10))
            isClickable = true
            isFocusable = false
            background =
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#12000000"))
                    setStroke(dp(3), Color.parseColor("#9E71F66B"))
                }
            addView(
                innerOrb,
                FrameLayout.LayoutParams(dp(88), dp(88), Gravity.CENTER),
            )
            setOnClickListener { triggerClearRecents() }
        }

        innerOrb.isClickable = true
        innerOrb.setOnClickListener { triggerClearRecents() }

        windowManager.addView(container, overlayLayoutParams())
        overlayView = container
        overlayTopLabel = topLabel
        overlayCenterLabel = centerLabel
        overlayBottomLabel = bottomLabel
        restartOverlayWatchdog()
    }

    private fun hideOverlay() {
        handler.removeCallbacks(overlayWatchdog)
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
        overlayTopLabel = null
        overlayCenterLabel = null
        overlayBottomLabel = null
    }

    private fun overlayLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            dp(108),
            dp(108),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // The overlay should be tappable but never steal focus from the launcher.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = dp(10)
        }
    }

    private fun triggerClearRecents() {
        if (clearingInProgress) {
            return
        }

        // A clear pass is stateful: once it starts we gate the overlay text,
        // remember that we want to return Home, and then step through the task list.
        clearingInProgress = true
        clearPassCount = 0
        returnHomeAfterClear = true
        restartOverlayWatchdog()
        setOverlayBusy(true)
        handler.post(clearRunnable)
    }

    private val clearRunnable =
        object : Runnable {
            override fun run() {
                val root = rootInActiveWindow
                if (!SettingsRepository.isEnabled(this@RecentsAccessibilityService)) {
                    abortClearRun()
                    return
                }

                if (root == null) {
                    clearPassCount += 1
                    if (clearPassCount < MAX_CLEAR_PASSES) {
                        handler.postDelayed(this, scaledDelay(PASS_DELAY_MS, MIN_PASS_DELAY_MS))
                    } else {
                        finishClearRun(returnHome = true)
                    }
                    return
                }

                if (isBlockedSurface(root)) {
                    abortClearRun()
                    return
                }

                if (!canAttemptClear(root)) {
                    finishClearRun(returnHome = true)
                    return
                }

                // Prefer the launcher's own action when available because it is
                // faster and more reliable than our synthetic swipe fallback.
                if (clickVisibleClearAll(root)) {
                    finishClearRun(
                        delayMs = scaledDelay(CLEAR_ALL_SETTLE_DELAY_MS, MIN_CLEAR_ALL_SETTLE_DELAY_MS),
                        returnHome = true,
                    )
                    return
                }

                val handled = dismissVisibleTask(root) || swipeAwayFallbackCard(root)
                clearPassCount += 1

                if (handled && clearPassCount < MAX_CLEAR_PASSES) {
                    handler.postDelayed(this, scaledDelay(PASS_DELAY_MS, MIN_PASS_DELAY_MS))
                } else {
                    finishClearRun(returnHome = true)
                }
            }
        }

    private fun finishClearRun(
        delayMs: Long = 0L,
        returnHome: Boolean = false,
    ) {
        handler.removeCallbacks(clearRunnable)
        if (delayMs == 0L) {
            resetClearState(returnHome)
        } else {
            handler.postDelayed({ resetClearState(returnHome) }, delayMs)
        }
    }

    private fun abortClearRun() {
        handler.removeCallbacks(clearRunnable)
        clearingInProgress = false
        returnHomeAfterClear = false
        lastRecentsSignalUptimeMs = 0L
        setOverlayBusy(false)
    }

    private fun resetClearState(returnHome: Boolean) {
        clearingInProgress = false
        val shouldReturnHome = returnHome && returnHomeAfterClear
        returnHomeAfterClear = false
        lastRecentsSignalUptimeMs = 0L
        setOverlayBusy(false)
        hideOverlay()
        overlaySuppressedUntilUptimeMs = SystemClock.uptimeMillis() + OVERLAY_SUPPRESSION_MS
        if (shouldReturnHome) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun setOverlayBusy(busy: Boolean) {
        // Updating the overlay copy makes it obvious to testers that the tap was
        // accepted, even if the launcher takes a beat to react.
        overlayTopLabel?.text = if (busy) "BabaG" else "BabaG"
        overlayCenterLabel?.text = if (busy) "SWEEP" else "CLEAR"
        overlayBottomLabel?.text = if (busy) "CLEARING" else "RECENTS"
        overlayView?.alpha = if (busy) 0.84f else 1f
        overlayView?.isEnabled = !busy
    }

    private fun shouldShowOverlay(
        event: AccessibilityEvent,
        root: AccessibilityNodeInfo?,
    ): Boolean {
        if (SystemClock.uptimeMillis() < overlaySuppressedUntilUptimeMs) {
            return false
        }

        if (root == null || isBlockedSurface(root) || isLauncherSurface(root)) {
            return false
        }

        return isExplicitRecentsSignal(event, root)
    }

    // Showing the overlay is stricter than clearing. We only show it on explicit
    // Recents signals so it stays out of Home, notifications, and lock surfaces.
    private fun isExplicitRecentsSignal(
        event: AccessibilityEvent,
        root: AccessibilityNodeInfo?,
    ): Boolean {
        val className = event.className?.toString().orEmpty()
        if (className.containsAnyHint(BLOCKED_SURFACE_HINTS)) {
            return false
        }
        if (className.containsAnyHint(RECENTS_CLASS_HINTS)) {
            return true
        }

        return root?.let(::hasExplicitRecentsMarkers) == true
    }

    // During a clear pass we allow slightly broader detection so the loop can finish
    // across tiny UI transitions without one missed event killing the run.
    private fun isLikelyRecents(root: AccessibilityNodeInfo): Boolean {
        val packageName = root.packageName?.toString().orEmpty()
        val className = root.className?.toString().orEmpty()

        if (isBlockedSurface(root)) {
            return false
        }

        if (className.containsAnyHint(RECENTS_CLASS_HINTS)) {
            return true
        }

        if (packageName !in LAUNCHER_PACKAGES) {
            return false
        }

        if (hasExplicitRecentsMarkers(root)) {
            return true
        }

        return findLargestTaskNode(
            root = root,
            requireDismissAction = false,
            allowBroadMatch = false,
        ) != null
    }

    // Explicit markers are what let the overlay appear in the first place.
    // These are the safest signals to use when deciding whether the user is
    // truly on Recents instead of another launcher surface.
    private fun hasExplicitRecentsMarkers(root: AccessibilityNodeInfo): Boolean {
        val packageName = root.packageName?.toString().orEmpty()
        if (packageName !in LAUNCHER_PACKAGES) {
            return false
        }

        if (isLauncherSurface(root)) {
            return false
        }

        val className = root.className?.toString().orEmpty()
        if (className.containsAnyHint(RECENTS_CLASS_HINTS)) {
            return true
        }

        if (findVisibleClearAllNode(root) != null) {
            return true
        }

        if (containsAnyNodeHint(root, RECENTS_ACTION_HINTS)) {
            return true
        }

        return containsAnyNodeHint(root, RECENTS_CONTAINER_HINTS)
    }

    private fun canAttemptClear(root: AccessibilityNodeInfo): Boolean {
        // The clear pass can continue briefly after one missed signal, but it must
        // stop immediately on blocked or launcher-like surfaces.
        if (isBlockedSurface(root) || isLauncherSurface(root)) {
            return false
        }

        if (isLikelyRecents(root)) {
            return true
        }

        if (SystemClock.uptimeMillis() - lastRecentsSignalUptimeMs > RECENTS_SIGNAL_GRACE_MS) {
            return false
        }

        return findVisibleClearAllNode(root) != null ||
            findLargestTaskNode(
                root = root,
                requireDismissAction = false,
                allowBroadMatch = false,
            ) != null
    }

    private fun clickVisibleClearAll(root: AccessibilityNodeInfo): Boolean {
        val node = findVisibleClearAllNode(root) ?: return false

        return clickNodeOrAncestor(node)
    }

    // Many launchers expose a hidden-but-clickable ancestor around the visible
    // "Clear all" text, so we locate the visible node and then walk upward later.
    private fun findVisibleClearAllNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return findFirstNode(root) { candidate ->
            candidate.isVisibleToUser &&
                candidate.nodeText().any { text ->
                    CLEAR_LABEL_HINTS.any { label -> text.contains(label, ignoreCase = true) }
                }
        }
    }

    private fun dismissVisibleTask(root: AccessibilityNodeInfo): Boolean {
        val target =
            findLargestTaskNode(
                root = root,
                requireDismissAction = true,
                allowBroadMatch = true,
            ) ?: return false

        return target.performAction(AccessibilityNodeInfo.ACTION_DISMISS) || swipeNodeUp(target)
    }

    // The fallback swipe is intentionally centered because several launchers ignore
    // lower swipes or treat them like drawer/navigation gestures.
    private fun swipeNodeUp(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty) {
            return false
        }

        val displayWidth = resources.displayMetrics.widthPixels.toFloat()
        val displayHeight = resources.displayMetrics.heightPixels.toFloat()
        val centerBandTop = displayHeight * 0.42f
        val centerBandBottom = displayHeight * 0.62f

        val startX = bounds.centerX().toFloat().coerceIn(displayWidth * 0.28f, displayWidth * 0.72f)
        val preferredStartY = bounds.top + (bounds.height() * 0.56f)
        val startY = preferredStartY.coerceIn(centerBandTop, centerBandBottom)
        val endY = displayHeight * 0.12f
        return dispatchVerticalSwipe(startX, startY, endY)
    }

    private fun swipeAwayFallbackCard(root: AccessibilityNodeInfo): Boolean {
        // Broad matching is allowed only while explicit Recents markers still exist;
        // once those disappear we stop instead of swiping Home or the app drawer.
        val allowBroadMatch = hasExplicitRecentsMarkers(root)
        val target =
            findLargestTaskNode(
                root = root,
                requireDismissAction = false,
                allowBroadMatch = allowBroadMatch,
            ) ?: return false
        return swipeNodeUp(target)
    }

    private fun dispatchVerticalSwipe(
        startX: Float,
        startY: Float,
        endY: Float,
    ): Boolean {
        val path =
            Path().apply {
                moveTo(startX, startY)
                lineTo(startX, endY)
            }

        val gesture =
            GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(
                        path,
                        0L,
                        scaledDelay(SWIPE_DURATION_MS, MIN_SWIPE_DURATION_MS),
                    ),
                )
                .build()

        return dispatchGesture(gesture, null, null)
    }

    // The slider is presented as 1x-10x, but we clamp the actual timing floors
    // so the gesture still looks human enough for Android to accept reliably.
    private fun scaledDelay(
        baseDelayMs: Long,
        minimumDelayMs: Long,
    ): Long {
        val multiplier = max(1, SettingsRepository.getSpeedMultiplier(this))
        return max(minimumDelayMs, baseDelayMs / multiplier)
    }

    private fun containsAnyText(
        node: AccessibilityNodeInfo,
        hints: List<String>,
    ): Boolean {
        // Text-based checks are useful for button labels and system strings.
        if (node.nodeText().any { text -> hints.any { hint -> text.contains(hint, ignoreCase = true) } }) {
            return true
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            if (containsAnyText(child, hints)) {
                return true
            }
        }

        return false
    }

    private fun containsAnyNodeHint(
        node: AccessibilityNodeInfo,
        hints: List<String>,
    ): Boolean {
        // Class-name/view-id checks are usually more stable than visible text
        // across OEMs, so we use both where possible.
        val className = node.className?.toString().orEmpty()
        val viewId = node.viewIdResourceName.orEmpty()
        if (className.containsAnyHint(hints) || viewId.containsAnyHint(hints)) {
            return true
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            if (containsAnyNodeHint(child, hints)) {
                return true
            }
        }

        return false
    }

    private fun findFirstNode(
        node: AccessibilityNodeInfo,
        matcher: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        // Depth-first search works well enough here because the node trees are
        // not huge and we care more about early visible matches than ordering.
        if (matcher(node)) {
            return node
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val match = findFirstNode(child, matcher)
            if (match != null) {
                return match
            }
        }

        return null
    }

    private fun collectNodes(
        node: AccessibilityNodeInfo,
        destination: MutableList<AccessibilityNodeInfo>,
        matcher: (AccessibilityNodeInfo) -> Boolean,
    ) {
        if (matcher(node)) {
            destination += node
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            collectNodes(child, destination, matcher)
        }
    }

    private fun findLargestTaskNode(
        root: AccessibilityNodeInfo,
        requireDismissAction: Boolean,
        allowBroadMatch: Boolean,
    ): AccessibilityNodeInfo? {
        // Picking the largest visible candidate biases us toward the foreground
        // Recents card instead of tiny background thumbnails or action chips.
        val taskNodes = mutableListOf<AccessibilityNodeInfo>()
        collectNodes(root, taskNodes) { node ->
            if (!node.isVisibleToUser || !isLargeEnough(node) || !isTaskLikeNode(node, allowBroadMatch)) {
                return@collectNodes false
            }

            if (!requireDismissAction) {
                return@collectNodes true
            }

            node.isDismissable ||
                node.actionList.any { action -> action.id == AccessibilityNodeInfo.ACTION_DISMISS }
        }

        return taskNodes.maxByOrNull { node ->
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            bounds.width() * bounds.height()
        }
    }

    private fun isTaskLikeNode(
        node: AccessibilityNodeInfo,
        allowBroadMatch: Boolean,
    ): Boolean {
        // Strong matches use known class/id/action hints. Broad matches fall back
        // to centered card-like geometry, which helps on OEM launchers that hide
        // better metadata from accessibility.
        val className = node.className?.toString().orEmpty()
        val viewId = node.viewIdResourceName.orEmpty()
        val packageName = node.packageName?.toString().orEmpty()
        val bounds = Rect().also(node::getBoundsInScreen)
        val displayWidth = resources.displayMetrics.widthPixels
        val displayHeight = resources.displayMetrics.heightPixels
        val centeredEnough =
            bounds.centerX() in (displayWidth * 0.18f).toInt()..(displayWidth * 0.82f).toInt() &&
                bounds.centerY() in (displayHeight * 0.18f).toInt()..(displayHeight * 0.82f).toInt()
        val cardSizedEnough =
            bounds.width() >= (displayWidth * 0.42f) &&
                bounds.height() >= (displayHeight * 0.22f)

        if (packageName == SYSTEM_UI_PACKAGE && (className.containsAnyHint(BLOCKED_SURFACE_HINTS) || viewId.containsAnyHint(BLOCKED_SURFACE_HINTS))) {
            return false
        }

        val strongMatch =
            className.containsAnyHint(TASK_NODE_HINTS) ||
            viewId.containsAnyHint(TASK_NODE_HINTS) ||
            node.isDismissable ||
            node.actionList.any { action -> action.id == AccessibilityNodeInfo.ACTION_DISMISS }

        return strongMatch || (allowBroadMatch && centeredEnough && cardSizedEnough)
    }

    private fun isBlockedSurface(
        root: AccessibilityNodeInfo?,
        eventClassName: String = "",
    ): Boolean {
        // Blocked surfaces are areas where the overlay should never appear or act,
        // even if SystemUI emits misleading events.
        if (keyguardManager.isKeyguardLocked) {
            return true
        }

        if (eventClassName.containsAnyHint(BLOCKED_SURFACE_HINTS)) {
            return true
        }

        root ?: return false
        val className = root.className?.toString().orEmpty()
        val packageName = root.packageName?.toString().orEmpty()

        if (className.containsAnyHint(BLOCKED_SURFACE_HINTS)) {
            return true
        }

        if (packageName == SYSTEM_UI_PACKAGE && containsAnyText(root, BLOCKED_SURFACE_HINTS)) {
            return true
        }

        return false
    }

    private fun isLauncherSurface(root: AccessibilityNodeInfo): Boolean {
        // Launcher surfaces are the home screen and app drawer. Treating them as
        // blocked prevents the service from continuing to swipe after Recents ends.
        val packageName = root.packageName?.toString().orEmpty()
        if (packageName !in LAUNCHER_PACKAGES) {
            return false
        }

        val className = root.className?.toString().orEmpty()
        val viewId = root.viewIdResourceName.orEmpty()

        if (className.containsAnyHint(LAUNCHER_SURFACE_HINTS) || viewId.containsAnyHint(LAUNCHER_SURFACE_HINTS)) {
            return true
        }

        return containsAnyNodeHint(root, LAUNCHER_SURFACE_HINTS)
    }

    private fun restartOverlayWatchdog() {
        handler.removeCallbacks(overlayWatchdog)
        handler.postDelayed(overlayWatchdog, OVERLAY_WATCHDOG_MS)
    }

    private fun clickNodeOrAncestor(node: AccessibilityNodeInfo): Boolean {
        // OEM launchers often place click behavior on a parent container, so we
        // walk up a few levels instead of requiring the visible label itself to
        // be clickable.
        var current: AccessibilityNodeInfo? = node
        repeat(6) {
            val target = current ?: return false
            if (target.isClickable && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            current = target.parent
        }
        return false
    }

    private fun isLargeEnough(node: AccessibilityNodeInfo): Boolean {
        // Tiny nodes are usually buttons, chips, or thumbnails, not the main
        // task card we want to dismiss.
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val widthThreshold = resources.displayMetrics.widthPixels * 0.22f
        val heightThreshold = resources.displayMetrics.heightPixels * 0.18f
        return bounds.width() >= widthThreshold && bounds.height() >= heightThreshold
    }

    private fun AccessibilityNodeInfo.nodeText(): List<String> {
        return buildList {
            text?.toString()?.takeIf(String::isNotBlank)?.let(::add)
            contentDescription?.toString()?.takeIf(String::isNotBlank)?.let(::add)
            viewIdResourceName?.takeIf(String::isNotBlank)?.let(::add)
        }
    }

    private fun String.containsAnyHint(hints: List<String>): Boolean {
        return hints.any { hint -> contains(hint, ignoreCase = true) }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    companion object {
        // These values are the first place to tune when a new launcher clears too
        // slowly, too aggressively, or keeps false-matching the wrong surface.
        private const val MAX_CLEAR_PASSES = 18
        private const val PASS_DELAY_MS = 90L
        private const val MIN_PASS_DELAY_MS = 24L
        private const val SWIPE_DURATION_MS = 110L
        private const val MIN_SWIPE_DURATION_MS = 45L
        private const val CLEAR_ALL_SETTLE_DELAY_MS = 220L
        private const val MIN_CLEAR_ALL_SETTLE_DELAY_MS = 90L
        private const val RECENTS_SIGNAL_GRACE_MS = 900L
        private const val OVERLAY_SUPPRESSION_MS = 1800L
        private const val OVERLAY_WATCHDOG_MS = 250L

        private val CLEAR_LABEL_HINTS =
            listOf(
                "clear all",
                "clear recents",
                "clear recent apps",
                "close all",
                "close apps",
                "remove all",
                "dismiss all",
            )

        private val RECENTS_ACTION_HINTS =
            listOf(
                "screenshot",
                "select",
                "split screen",
                "clear all",
                "close all",
            )

        private val RECENTS_CONTAINER_HINTS =
            listOf(
                "overview",
                "recents",
                "recent apps",
                "recent tasks",
                "taskview",
                "actions_view",
                "snapshot",
            )

        private val RECENTS_CLASS_HINTS =
            listOf(
                "recents",
                "overview",
                "recenttasks",
                "recenttask",
                "taskview",
                "fallbackrecents",
            )

        private val TASK_NODE_HINTS =
            listOf(
                "task",
                "taskview",
                "overview",
                "recents",
                "card",
                "snapshot",
                "thumbnail",
            )

        private val BLOCKED_SURFACE_HINTS =
            listOf(
                "notification",
                "statusbar",
                "keyguard",
                "lockscreen",
                "lock_icon",
                "shade",
                "bouncer",
                "scrim",
                "qs_",
            )

        private val LAUNCHER_SURFACE_HINTS =
            listOf(
                "workspace",
                "hotseat",
                "launcherrootview",
                "allapps",
                "apps_view",
                "appdrawer",
                "search_container",
                "floating_search",
                "celllayout",
                "bubbletextview",
                "widget_cell",
                "predictions",
            )

        private val LAUNCHER_PACKAGES =
            setOf(
                "com.android.systemui",
                "com.google.android.apps.nexuslauncher",
                "com.android.launcher3",
                "com.miui.home",
                "com.sec.android.app.launcher",
                "com.oppo.launcher",
                "com.coloros.launcher",
                "com.vivo.launcher",
                "com.huawei.android.launcher",
                "com.motorola.launcher3",
            )

        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    }
}
