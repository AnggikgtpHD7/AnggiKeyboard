-keep public class * extends android.inputmethodservice.InputMethodService
-keep public class * extends android.app.Activity

-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
