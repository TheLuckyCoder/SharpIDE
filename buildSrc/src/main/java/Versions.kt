@file:Suppress("unused")

object Versions {
    const val build_tools = "28.0.2"
    const val min_sdk = 16
    const val compile_sdk = 28
    const val target_sdk = 26

    const val kotlin_version = "1.2.70"
    const val support_lib = "28.0.0-rc02"
}

object Libs {
    // Kotlin
    const val kotlin_stdlib = "org.jetbrains.kotlin:kotlin-stdlib:${Versions.kotlin_version}"
    const val kotlin_coroutines = "org.jetbrains.kotlinx:kotlinx-coroutines-android:0.26.0"

    // Support Libraries
    private const val support_v4 = "com.android.support:support-v4:${Versions.support_lib}"
    private const val support_appcompat = "com.android.support:appcompat-v7:${Versions.support_lib}"
    private const val support_design = "com.android.support:design:${Versions.support_lib}"
    @JvmStatic
    val libs_support = arrayOf(support_v4, support_appcompat, support_design)

    // Firebase
    const val firebase_core = "com.google.firebase:firebase-core:16.0.3"
    const val firebase_config = "com.google.firebase:firebase-config:16.0.0"
    const val firebase_ads = "com.google.firebase:firebase-ads:15.0.1"

    const val fabric_crashlytics = "com.crashlytics.sdk.android:crashlytics:2.9.5@aar"
    const val material_chooser = "net.theluckycoder.materialchooser:materialchooser:1.1.5"
    const val license_dialog = "de.psdev.licensesdialog:licensesdialog:1.8.3"
    const val support_preference_fix = "com.takisoft.fix:preference-v7:28.0.0.0-rc02"
    const val keyboard_visibility = "net.yslibrary.keyboardvisibilityevent:keyboardvisibilityevent:2.1.0"
}
