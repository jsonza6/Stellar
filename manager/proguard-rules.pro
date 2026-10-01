-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
	public static void check*(...);
	public static void throw*(...);
}

-assumenosideeffects class java.util.Objects{
    ** requireNonNull(...);
}

-keepnames class com.stellar.api.BinderContainer

-keepclassmembers class rikka.hidden.compat.adapter.ProcessObserverAdapter {
    <methods>;
}

-keepclassmembers class rikka.hidden.compat.adapter.UidObserverAdapter {
    <methods>;
}

-keep class roro.stellar.server.StellarService {
    public static void main(java.lang.String[]);
}

-keep class roro.stellar.server.bootstrap.ServerBootstrap {
    public static void main(java.lang.String[]);
}

# Keep UserServiceStarter for app_process
-keep class roro.stellar.server.userservice.UserServiceStarter {
    public static void main(java.lang.String[]);
}

-assumenosideeffects class android.util.Log {
    public static *** d(...);
}

-assumenosideeffects class roro.stellar.manager.util.Logger {
    public *** d(...);
}

-assumenosideeffects class roro.stellar.server.util.Logger {
    public *** d(...);
}

-allowaccessmodification
-repackageclasses roro.stellar
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Fix R8 missing classes for androidx.window
# Miuix pulls androidx.window (via material3-window-size-class); the sidecar classes
# only exist on devices that ship the WindowManager sidecar, so they are optional at
# compile time and must not fail the R8 minification step.
-dontwarn androidx.window.sidecar.**
-dontwarn androidx.window.extensions.**
-dontwarn androidx.window.area.**
-dontwarn androidx.window.reflection.**
-dontwarn androidx.window.core.util.function.**
