// Versions are pinned to known-good releases. Android Studio may offer newer
// ones via "Upgrade Assistant" / quick fixes; accepting them is fine.
plugins {
    id("com.android.application") version "8.11.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0" apply false
    id("com.google.devtools.ksp") version "2.2.0-2.0.2" apply false
    id("com.google.gms.google-services") version "4.4.3" apply false
}
