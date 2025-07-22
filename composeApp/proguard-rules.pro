-printconfiguration build/tmp/full-r8-config.txt
-verbose

-keep class com.llamatik.app.** { *; }
-keep public class * extends java.lang.Exception

-keep @androidx.annotation.Keep public class *

-dontwarn com.google.android.material.**
-keep class com.google.android.material.** { *; }

-dontwarn androidx.**
-keep class androidx.** { *; }
-keep class androidx.core.app.CoreComponentFactory { *; }
-keep interface androidx.** { *; }

-keep class android.content.res.** { *; }
-keepclassmembers class android.content.res.** { *; }

-keep class androidx.core.content.res.** { *; }
-keepclassmembers class androidx.core.content.res.** { *; }

-keepclassmembers class * implements android.os.Parcelable {
 public <fields>;
}

# OkHttp
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**


-dontwarn java.nio.**
-dontwarn java.lang.**
-keepattributes Signature, Exceptions, Annotation

# Firebase
-keep class com.google.firebase { *; }
-keep class com.google.android.gms { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
# https://support.appsflyer.com/hc/en-us/articles/210289286#6-using-proguard-with-uninstall-tracking
-keep public class com.google.firebase.iid.FirebaseInstanceId {
    public *;
}
-dontwarn com.google.j2objc.annotations.RetainedWith
-dontwarn com.google.j2objc.annotations.Weak

# Kotlin from https://medium.com/@maheshwar.ligade/progurad-for-android-kotlin-104a1169fdcd
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
}
-keepclassmembers public class com.cypressworks.kotlinreflectionproguard.** {
    public * *;
}

# Kotlin Couroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

-keep class com.gemalto.jp2.** { *; }
-keep class java.awt.event.** { *; }
-keep class java.net.http.** { *; }
-keep class javax.swing.** { *; }
-keep class org.slf4j.impl.** { *; }
-keep class io.kamel.** { *; }

-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn java.awt.event.ActionListener
-dontwarn java.net.http.HttpClient$Builder
-dontwarn java.net.http.HttpClient$Redirect
-dontwarn java.net.http.HttpClient$Version
-dontwarn java.net.http.HttpClient
-dontwarn java.net.http.HttpConnectTimeoutException
-dontwarn java.net.http.HttpRequest$BodyPublisher
-dontwarn java.net.http.HttpRequest$BodyPublishers
-dontwarn java.net.http.HttpRequest$Builder
-dontwarn java.net.http.HttpRequest
-dontwarn java.net.http.HttpResponse$BodyHandler
-dontwarn java.net.http.HttpResponse
-dontwarn java.net.http.HttpTimeoutException
-dontwarn java.net.http.WebSocket$Builder
-dontwarn java.net.http.WebSocket$Listener
-dontwarn java.net.http.WebSocket
-dontwarn javax.swing.SwingUtilities
-dontwarn javax.swing.Timer
-dontwarn org.slf4j.impl.StaticLoggerBinder
