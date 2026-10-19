-keepattributes *Annotation*
-dontwarn javax.annotation.**
# LiteRT-LM's JNI code resolves SDK classes and callback methods by name.
-keep class com.google.ai.edge.litertlm.** { *; }
