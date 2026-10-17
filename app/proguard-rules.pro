# sherpa-onnx calls back into these Kotlin classes from native code by name.
-keep class com.k2fsa.sherpa.onnx.** { *; }

# ONNX Runtime's Java API is reached from JNI.
-keep class ai.onnxruntime.** { *; }

# kotlinx.serialization: keep generated serializers for our saved project files.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class io.github.rdilipkumar.subtitlestudio.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.rdilipkumar.subtitlestudio.**$$serializer { *; }
