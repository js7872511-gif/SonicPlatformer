package com.studiodroid.app

import android.content.Intent
import android.content.pm.PackageManager

object WindowsLayerDetector {
    val knownPackages = listOf(
        "com.winlator",
        "com.winlator.cmod",
        "com.winlator.glibc",
        "com.winlator.amod"
    )

    fun firstSupported(installedPackages: Set<String>): String? =
        knownPackages.firstOrNull { it in installedPackages }

    fun detect(packageManager: PackageManager): Pair<String, Intent>? {
        knownPackages.forEach { packageName ->
            packageManager.getLaunchIntentForPackage(packageName)?.let { return packageName to it }
        }
        return null
    }
}
