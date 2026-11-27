package com.anggik.keyboard.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.TargetApi
import android.app.Activity
import android.os.Build
import android.view.animation.AccelerateInterpolator

@TargetApi(Build.VERSION_CODES.S)
object SplashBridge {

    fun attachExitAnimation(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        activity.splashScreen.setOnExitAnimationListener { splashView ->
            splashView.animate()
                .alpha(0f)
                .scaleX(1.06f)
                .scaleY(1.06f)
                .setDuration(150L)
                .setInterpolator(AccelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        splashView.remove()
                    }
                })
                .start()
        }
    }
}
