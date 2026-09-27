# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keep class io.element.android.libraries.designsystem.showkase.DesignSystemShowkaseRootModuleCodegen { }

# Family Chat (parental gate): ParentalGateSafeContent turns off the Process-Text items of Compose text menus through
# this internal test hook, by reflection.
-keep class androidx.compose.foundation.text.contextmenu.ProcessTextApi23Impl {
    public static ** INSTANCE;
    public void setProcessTextActivitiesQuery(kotlin.jvm.functions.Function1);
}
