-keepattributes Signature,*Annotation*
-keep class com.cinemawatch.data.** { *; }

# Release instrumentation calls public application entry points (including Kotlin
# default-argument bridges and the updater injection seam). Preserve that callable
# surface in the exact candidate we publish; still allow name obfuscation and shrink
# dependencies/private implementation. The test APK applies the release mapping.
-keep,allowobfuscation class com.cinemawatch.** { public *; protected *; }
-keep,allowobfuscation enum app.fieldwatch.domain.SignatureClass { *; }

# The separately packaged AndroidJUnitRunner/Compose test libraries call these
# shared runtime APIs. AGP removes duplicate dependencies from the test APK;
# optimizing them away in the target caused NoClassDefFoundError (Trace) and
# leaves other test calls without a target. Keep the shared ABI in the shipped
# candidate too. Icon families and unrelated dependencies remain shrinkable.
-keep class androidx.tracing.** { *; }
-keep class androidx.concurrent.futures.** { *; }
-keep interface com.google.common.util.concurrent.ListenableFuture { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.material3.MaterialThemeKt { *; }
-keep class androidx.activity.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.savedstate.** { *; }
-keep class androidx.core.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
