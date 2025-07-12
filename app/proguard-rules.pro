# ===============================================
# == Regras do ProGuard para a biblioteca LibVLC ==
# ===============================================
# Mantém todas as classes no pacote principal da LibVLC e as suas subclasses.
-keep class org.videolan.libvlc.** { *; }
-keep class org.videolan.vlc.** { *; }

# Mantém os nomes dos métodos nativos para que possam ser encontrados pelo JNI.
-keepclassmembers class org.videolan.libvlc.** {
    native <methods>;
}

# Mantém os construtores de classes que podem ser instanciadas via reflexão.
-keepclassmembers class org.videolan.vlc.** {
    <init>(...);
}


# ===========================================
# == Regras para a biblioteca Glide (Imagens) ==
# ===========================================
# Mantém as classes geradas pelo Glide e as suas anotações.
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep,allowobfuscation public enum com.bumptech.glide.load.ImageHeaderParser$ImageType {
  **[] $VALUES;
  public *;
}


# ====================================================================
# == Regras para a biblioteca Gson (JSON) e as suas Classes de Modelo ==
# ====================================================================
# Mantém os nomes dos campos nas suas classes de modelo (data classes)
# para que o Gson possa mapear o JSON corretamente.
# Substitua 'com.jorgenascimento.tvplayer.data.model.**' se o seu pacote for diferente.
-keepclassmembers class com.jorgenascimento.tvplayer.data.model.** {
  <fields>;
}


# ====================================================
# == Regras para Classes Parcelable (ex: com @Parcelize) ==
# ====================================================
# Mantém a classe e o seu campo estático CREATOR, que é necessário
# para o Android recriar o objeto.
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}


# ===============================================================
# == Regras Adicionais (Geralmente já cobertas, mas boas para garantir) ==
# ===============================================================
# Mantém anotações que podem ser usadas em tempo de execução.
-keepattributes *Annotation*

# Mantém os nomes de classes de Activities, Services, etc., para que o sistema possa encontrá-las.
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.Application
-keep public class * extends androidx.core.app.CoreComponentFactory
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
# SR_CORRECTION: A linha abaixo foi removida pois a biblioteca de licenciamento não está a ser usada.
# -keep public class com.android.vending.licensing.ILicensingService

# Mantém os construtores de Views para que possam ser inflados a partir do XML.
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}