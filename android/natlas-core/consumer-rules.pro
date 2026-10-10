# kotlinx.serialization: keep generated serializers of the SDK's wire models.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class io.github.baffaabba.natlas.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.baffaabba.natlas.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class io.github.baffaabba.natlas.internal.NAtlasInitProvider
