package com.anggik.keyboard.ui

import android.annotation.TargetApi
import android.os.Build
import android.view.Window
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

@TargetApi(Build.VERSION_CODES.TIRAMISU)
class DrawerBackDispatcher(
    private val onCollapseDrawer: () -> Boolean
) {
    private var registeredDispatcher: OnBackInvokedDispatcher? = null
    private var callback: OnBackInvokedCallback? = null

    fun setDrawerOpen(window: Window?, isOpen: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (!isOpen) {
            detach()
            return
        }
        val dispatcher = window?.onBackInvokedDispatcher ?: return
        if (registeredDispatcher === dispatcher && callback != null) return

        detach()
        val cb = OnBackInvokedCallback {
            onCollapseDrawer()
            detach()
        }
        dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, cb)
        registeredDispatcher = dispatcher
        callback = cb
    }

    fun detach() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val disp = registeredDispatcher
        val cb = callback
        if (disp != null && cb != null) {
            try {
                disp.unregisterOnBackInvokedCallback(cb)
            } catch (_: Exception) {
                // Abaikan jika window sudah dilepas sistem
            }
        }
        registeredDispatcher = null
        callback = null
    }
}
