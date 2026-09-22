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

# 카카오맵 SDK(com.kakao.maps.open:android) — AAR에 내장된 consumer proguard 규칙이 전혀 없고
# libK3fAndroid.so(JNI 네이티브 라이브러리)를 함께 쓰는 SDK라 R8이 이름을 바꾸면 네이티브 콜백이
# 못 찾는 클래스/메서드가 생길 수 있다. Retrofit/OkHttp/kotlinx.serialization/ML Kit GenAI/
# Firebase AI는 전부 자체 consumer 규칙을 내장하고 있어서(각 AAR/jar 안 proguard.txt 확인 완료)
# 여기서 따로 안 적어도 된다.
-keep class com.kakao.vectormap.** { *; }
-dontwarn com.kakao.vectormap.**