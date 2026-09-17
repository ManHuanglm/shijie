# Add project specific ProGuard rules here.
-keep class com.shiping.app.data.model.** { *; }

# 保留泛型签名：Retrofit 解析 suspend 方法返回类型（Continuation<ApiResponse<T>>）
# 与 Gson 反序列化泛型字段都依赖 Signature 属性；新版 R8 会剥离未显式保留的签名，
# 导致运行时 "Class cannot be cast to ParameterizedType"
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# Retrofit 官方规则
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowobfuscation interface <1>
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Gson：TypeToken 泛型与 Type 实现
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements java.lang.reflect.Type
