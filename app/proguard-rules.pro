# ProGuard 混淆规则
# 定位跟踪应用

# 保留 Kotlin 元数据
-keep class kotlin.Metadata { *; }

# 保留数据类
-keep class com.tracker.location.data.** { *; }

# 保留 Gson 序列化
-keepattributes Signature
-keepattributes *Annotation*

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
