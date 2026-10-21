-keepattributes Signature,*Annotation*
-keep class com.cinemawatch.data.** { *; }

# Release instrumentation calls public application entry points (including Kotlin
# default-argument bridges and the updater injection seam). Preserve that callable
# surface in the exact candidate we publish; still allow name obfuscation and shrink
# dependencies/private implementation. The test APK applies the release mapping.
-keep,allowobfuscation class com.cinemawatch.** { public *; protected *; }
-keep,allowobfuscation enum app.fieldwatch.domain.SignatureClass { *; }
