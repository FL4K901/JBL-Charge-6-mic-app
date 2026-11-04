#include <jni.h>
#include "OboeAudioEngine.h"

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_mic_app_mic_OboeAudioEngineBridge_createEngine(JNIEnv *env, jobject thiz) {
    auto *engine = new (std::nothrow) OboeAudioEngine();
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT jboolean JNICALL
Java_com_mic_app_mic_OboeAudioEngineBridge_startEngine(JNIEnv *env, jobject thiz, jlong engine_handle) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engine_handle);
    if (!engine) return JNI_FALSE;
    return engine->start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_mic_app_mic_OboeAudioEngineBridge_stopEngine(JNIEnv *env, jobject thiz, jlong engine_handle) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engine_handle);
    if (engine) engine->stop();
}

JNIEXPORT void JNICALL
Java_com_mic_app_mic_OboeAudioEngineBridge_setBoostGain(JNIEnv *env, jobject thiz, jlong engine_handle, jfloat gain) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engine_handle);
    if (engine) engine->setBoostGain(gain);
}

JNIEXPORT void JNICALL
Java_com_mic_app_mic_OboeAudioEngineBridge_setEcho(JNIEnv *env, jobject thiz, jlong engine_handle, jfloat wet, jint delay_ms, jfloat feedback) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engine_handle);
    if (engine) engine->setEcho(wet, delay_ms, feedback);
}

JNIEXPORT void JNICALL
Java_com_mic_app_mic_OboeAudioEngineBridge_setReverb(JNIEnv *env, jobject thiz, jlong engine_handle, jfloat wet, jfloat room_size) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engine_handle);
    if (engine) engine->setReverb(wet, room_size);
}

JNIEXPORT void JNICALL
Java_com_mic_app_mic_OboeAudioEngineBridge_setMute(JNIEnv *env, jobject thiz, jlong engine_handle, jboolean mute) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engine_handle);
    if (engine) engine->setMute(mute == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_mic_app_mic_OboeAudioEngineBridge_setAntiHowling(JNIEnv *env, jobject thiz, jlong engine_handle, jboolean enabled) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engine_handle);
    if (engine) engine->setAntiHowling(enabled == JNI_TRUE);
}

JNIEXPORT jfloat JNICALL
Java_com_mic_app_mic_OboeAudioEngineBridge_getRms(JNIEnv *env, jobject thiz, jlong engine_handle) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engine_handle);
    return engine ? engine->getRms() : 0.0f;
}

JNIEXPORT jfloat JNICALL
Java_com_mic_app_mic_OboeAudioEngineBridge_getPeak(JNIEnv *env, jobject thiz, jlong engine_handle) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engine_handle);
    return engine ? engine->getPeak() : 0.0f;
}

JNIEXPORT jboolean JNICALL
Java_com_mic_app_mic_OboeAudioEngineBridge_isHowling(JNIEnv *env, jobject thiz, jlong engine_handle) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engine_handle);
    return (engine && engine->isHowling()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_mic_app_mic_OboeAudioEngineBridge_isClipping(JNIEnv *env, jobject thiz, jlong engine_handle) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engine_handle);
    return (engine && engine->isClipping()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_mic_app_mic_OboeAudioEngineBridge_deleteEngine(JNIEnv *env, jobject thiz, jlong engine_handle) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engine_handle);
    delete engine;
}

}
