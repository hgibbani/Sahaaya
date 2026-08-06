# ---------------------------------------------------------------------------
# SAHAAYA - release shrinking rules
# ---------------------------------------------------------------------------

# Domain models are reflected over by Firestore's serialiser when a document is
# read into a typed object, and their field names are the document keys. R8
# renaming them turns every read into a null field, which in this app means a
# caregiver seeing an empty medical profile rather than a crash - a far worse
# failure, because nothing looks broken.
-keep class com.sahaaya.domain.model.** { *; }
-keepclassmembers class com.sahaaya.domain.model.** {
    <init>(...);
    <fields>;
}

# Firestore and Auth use reflection internally.
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes AnnotationDefault

-keepnames class com.google.firebase.** { *; }
-keepclassmembers class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# Hilt / Dagger generate classes referenced by name.
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
