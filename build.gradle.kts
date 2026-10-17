plugins {
    // AGP 9 compiles Kotlin itself ("built-in Kotlin"): no separate Kotlin plugin.
    id("com.android.application") version "9.4.1" apply false
    // Must match the Kotlin version bundled with AGP (2.2.10 for AGP 9.4.1).
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
