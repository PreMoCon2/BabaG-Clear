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
import kotlin.math.roundToInt

class RecentsAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
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
    private var lastRecentsSignalUptimeMs = 0L
    private var overlaySuppressedUntilUptimeMs = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WindowManager::class.java)
        keyguardManager = getSystemService(KeyguardManager::class.java)
        serviceInfo = serviceInfo.apply {
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
                        handler.postDelayed(this, PASS_DELAY_MS)
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

                if (clickVisibleClearAll(root)) {
                    finishClearRun(delayMs = 350L, returnHome = true)
                    return
                }

                val handled = dismissVisibleTask(root) || swipeAwayFallbackCard(root)
                clearPassCount += 1

                if (handled && clearPassCount < MAX_CLEAR_PASSES) {
                    handler.postDelayed(this, PASS_DELAY_MS)
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
                .addStroke(GestureDescription.StrokeDescription(path, 0L, 160L))
                .build()

        return dispatchGesture(gesture, null, null)
    }

    private fun containsAnyText(
        node: AccessibilityNodeInfo,
        hints: List<String>,
    ): Boolean {
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
        private const val MAX_CLEAR_PASSES = 18
        private const val PASS_DELAY_MS = 140L
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
