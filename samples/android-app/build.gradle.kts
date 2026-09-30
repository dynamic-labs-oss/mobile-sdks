// Root build file: declare plugin versions once, apply them in :app.
plugins {
    id("com.android.application") version "8.7.3" apply false
    // Needed by packages/android-{core,sdk,evm}, included as subprojects below.
    id("com.android.library") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    // The generated SDK core's models are @Serializable (kotlinx.serialization) —
    // this compiler plugin generates their KSerializer.
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
}

allprojects {
    repositories {
        maven { url = rootProject.uri("../../kotlin/maven") }
    }
}
