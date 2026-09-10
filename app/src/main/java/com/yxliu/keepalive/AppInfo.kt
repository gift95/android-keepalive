package com.yxliu.keepalive

import android.graphics.drawable.Drawable

/** Lightweight model representing an installable app shown in the selection list. */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystem: Boolean,
    var selected: Boolean = false
)
