# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
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

# ===============================================
# == Regras do ProGuard para a biblioteca LibVLC ==
# ===============================================
# Mantém todas as classes no pacote principal da LibVLC e as suas subclasses.
-keep class org.videolan.libvlc.** { *; }

# Mantém as classes da interface nativa (JNI) e os seus membros.
# Isto é crucial para a comunicação entre o código Kotlin/Java e o código nativo C/C++.
-keep class org.videolan.vlc.** { *; }

# Mantém os nomes dos métodos nativos para que possam ser encontrados pelo JNI.
-keepclassmembers class org.videolan.libvlc.** {
    native <methods>;
}

# Mantém os construtores de classes que podem ser instanciadas via reflexão.
-keepclassmembers class org.videolan.vlc.** {
    <init>(...);
}