# Consumer ProGuard rules for Llamatik library.

# xmlutil has JDK-only helper classes that are never used on Android
-dontwarn nl.adaptivity.xmlutil.jdk.**

# MethodHandle.invoke signature is not modeled by R8 on Android
-dontwarn java.lang.invoke.MethodHandle
