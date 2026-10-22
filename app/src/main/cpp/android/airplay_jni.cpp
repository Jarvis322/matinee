// Bridges UxPlay's AirPlay protocol library to Kotlin: it runs the RAOP/AirPlay server and passes the decrypted
// H.264 and audio frames up as byte arrays; decoding and drawing happen in Kotlin with MediaCodec.
#include <jni.h>
#include <android/log.h>
#include <pthread.h>
#include <stdio.h>
#include <string.h>

extern "C" {
#include "raop.h"
#include "dnssd.h"
#include "stream.h"
#include "dns_sd.h"
}

#define TAG "MatineeAirPlay"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

JavaVM *vm;
jobject listener;
jmethodID onService, onVideo, onAudio, onConnection, onVolume, onFlush, onAudioFormat, onClient;
pthread_key_t detach_key;
raop_t *raop;
dnssd_t *dnssd;
float current_volume = 0.0f;

void detach_thread(void *) { vm->DetachCurrentThread(); }

JNIEnv *env_for_thread() {
    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK) return env;
    if (vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return nullptr;
    pthread_setspecific(detach_key, env);
    return env;
}

jbyteArray bytes(JNIEnv *env, const void *data, int length) {
    jbyteArray array = env->NewByteArray(length);
    env->SetByteArrayRegion(array, 0, length, static_cast<const jbyte *>(data));
    return array;
}

void service_hook(int registered, const char *name, const char *regtype, uint16_t port, const uint8_t *txt, uint16_t txt_len) {
    JNIEnv *env = env_for_thread();
    if (!env || !listener) return;
    jstring jname = env->NewStringUTF(name);
    jstring jtype = env->NewStringUTF(regtype);
    jbyteArray jtxt = bytes(env, txt, txt_len);
    env->CallVoidMethod(listener, onService, registered != 0, jname, jtype, static_cast<jint>(port), jtxt);
    env->DeleteLocalRef(jname);
    env->DeleteLocalRef(jtype);
    env->DeleteLocalRef(jtxt);
}

void log_callback(void *, int level, const char *msg) {
    __android_log_print(level <= 3 ? ANDROID_LOG_ERROR : ANDROID_LOG_DEBUG, TAG, "%s", msg);
}

void conn_init(void *) {
    if (JNIEnv *env = env_for_thread()) env->CallVoidMethod(listener, onConnection, JNI_TRUE);
}

void conn_destroy(void *) {
    if (JNIEnv *env = env_for_thread()) env->CallVoidMethod(listener, onConnection, JNI_FALSE);
}

void conn_feedback(void *) {}

void conn_reset(void *, int reason) {
    LOGI("connection reset, reason %d", reason);
}

void audio_process(void *, raop_ntp_t *, audio_decode_struct *data) {
    JNIEnv *env = env_for_thread();
    if (!env) return;
    jbyteArray array = bytes(env, data->data, data->data_len);
    env->CallVoidMethod(listener, onAudio, array, static_cast<jint>(data->ct), static_cast<jlong>(data->ntp_time_remote),
                        static_cast<jint>(data->seqnum));
    env->DeleteLocalRef(array);
}

void video_process(void *, raop_ntp_t *, video_decode_struct *data) {
    JNIEnv *env = env_for_thread();
    if (!env) return;
    // The first byte of valid data is 0x00 (a start code); anything else means decryption failed.
    if (data->data_len <= 0 || data->data[0] != 0) return;
    jbyteArray array = bytes(env, data->data, data->data_len);
    env->CallVoidMethod(listener, onVideo, array, static_cast<jlong>(data->ntp_time_remote));
    env->DeleteLocalRef(array);
}

void audio_flush(void *) {
    if (JNIEnv *env = env_for_thread()) env->CallVoidMethod(listener, onFlush, JNI_TRUE);
}

void video_flush(void *) {
    if (JNIEnv *env = env_for_thread()) env->CallVoidMethod(listener, onFlush, JNI_FALSE);
}

void video_pause(void *) {}
void video_resume(void *) {}
void video_reset(void *, reset_type_t) {}
void video_report_size(void *, float *, float *, float *, float *) {}
void audio_set_metadata(void *, const void *, int) {}
void audio_set_coverart(void *, const void *, int) {}
void audio_stop_coverart_rendering(void *) {}
void audio_set_progress(void *, uint32_t *, uint32_t *, uint32_t *) {}
void display_pin(void *, char *) {}
void register_client(void *, const char *, const char *, const char *) {}
bool check_register(void *, const char *) { return true; }
void export_dacp(void *, const char *, const char *) {}

const char *passwd(void *, int *len) {
    *len = 0;
    return nullptr;
}

int video_set_codec(void *, video_codec_t codec) { return codec == VIDEO_CODEC_H264 ? 0 : -1; }

double audio_set_client_volume(void *) { return current_volume; }

void audio_set_volume(void *, float volume) {
    current_volume = volume;
    if (JNIEnv *env = env_for_thread()) env->CallVoidMethod(listener, onVolume, volume);
}

void audio_get_format(void *, unsigned char *ct, unsigned short *spf, bool *, bool *, uint64_t *) {
    if (JNIEnv *env = env_for_thread()) env->CallVoidMethod(listener, onAudioFormat, static_cast<jint>(*ct), static_cast<jint>(*spf));
}

void report_client_request(void *, char *, char *model, char *name, bool *admit) {
    *admit = true;
    JNIEnv *env = env_for_thread();
    if (!env) return;
    jstring jname = env->NewStringUTF(name ? name : "");
    jstring jmodel = env->NewStringUTF(model ? model : "");
    env->CallVoidMethod(listener, onClient, jname, jmodel);
    env->DeleteLocalRef(jname);
    env->DeleteLocalRef(jmodel);
}

// The feature set UxPlay advertises for mirroring and audio, without AirPlay video (HLS) or H.265.
void set_features(dnssd_t *d) {
    static const int on[] = {1, 2, 5, 6, 7, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 25, 27, 28, 30};
    for (int bit = 0; bit < 32; bit++) dnssd_set_airplay_features(d, bit, 0);
    for (int bit : on) dnssd_set_airplay_features(d, bit, 1);
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *jvm, void *) {
    vm = jvm;
    pthread_key_create(&detach_key, detach_thread);
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tcltv_sahne_airplay_AirPlayNative_start(JNIEnv *env, jobject self, jstring jname, jbyteArray jhw, jstring jkeyfile) {
    if (raop) return -1;
    listener = env->NewGlobalRef(self);
    jclass cls = env->GetObjectClass(self);
    onService = env->GetMethodID(cls, "onService", "(ZLjava/lang/String;Ljava/lang/String;I[B)V");
    onVideo = env->GetMethodID(cls, "onVideo", "([BJ)V");
    onAudio = env->GetMethodID(cls, "onAudio", "([BIJI)V");
    onConnection = env->GetMethodID(cls, "onConnection", "(Z)V");
    onVolume = env->GetMethodID(cls, "onVolume", "(F)V");
    onFlush = env->GetMethodID(cls, "onFlush", "(Z)V");
    onAudioFormat = env->GetMethodID(cls, "onAudioFormat", "(II)V");
    onClient = env->GetMethodID(cls, "onClient", "(Ljava/lang/String;Ljava/lang/String;)V");

    matinee_set_service_hook(service_hook);

    const char *name = env->GetStringUTFChars(jname, nullptr);
    const char *keyfile = env->GetStringUTFChars(jkeyfile, nullptr);
    jbyte hw[6] = {0};
    env->GetByteArrayRegion(jhw, 0, 6, hw);
    char device_id[18];
    snprintf(device_id, sizeof(device_id), "%02x:%02x:%02x:%02x:%02x:%02x", hw[0] & 0xff, hw[1] & 0xff, hw[2] & 0xff,
             hw[3] & 0xff, hw[4] & 0xff, hw[5] & 0xff);

    raop_callbacks_t cbs;
    memset(&cbs, 0, sizeof(cbs));
    cbs.conn_init = conn_init;
    cbs.conn_destroy = conn_destroy;
    cbs.conn_reset = conn_reset;
    cbs.conn_feedback = conn_feedback;
    cbs.audio_process = audio_process;
    cbs.video_process = video_process;
    cbs.audio_flush = audio_flush;
    cbs.video_flush = video_flush;
    cbs.video_pause = video_pause;
    cbs.video_resume = video_resume;
    cbs.audio_set_client_volume = audio_set_client_volume;
    cbs.audio_set_volume = audio_set_volume;
    cbs.audio_get_format = audio_get_format;
    cbs.video_report_size = video_report_size;
    cbs.audio_set_metadata = audio_set_metadata;
    cbs.audio_set_coverart = audio_set_coverart;
    cbs.audio_stop_coverart_rendering = audio_stop_coverart_rendering;
    cbs.audio_set_progress = audio_set_progress;
    cbs.report_client_request = report_client_request;
    cbs.display_pin = display_pin;
    cbs.register_client = register_client;
    cbs.check_register = check_register;
    cbs.passwd = passwd;
    cbs.export_dacp = export_dacp;
    cbs.video_reset = video_reset;
    cbs.video_set_codec = video_set_codec;

    int result = 0;
    int dnssd_error = 0;
    raop = raop_init(&cbs);
    if (!raop) {
        result = -2;
    } else {
        raop_set_log_callback(raop, log_callback, nullptr);
        raop_set_log_level(raop, 6);
        if (raop_init2(raop, 1, device_id, keyfile)) {
            result = -3;
        } else {
            unsigned short tcp[3] = {0, 0, 0};
            unsigned short udp[3] = {0, 0, 0};
            raop_set_plist(raop, "width", 1920);
            raop_set_plist(raop, "height", 1080);
            raop_set_plist(raop, "refreshRate", 60);
            raop_set_plist(raop, "maxFPS", 30);
            raop_set_tcp_ports(raop, tcp);
            raop_set_udp_ports(raop, udp);
            unsigned short port = raop_get_port(raop);
            raop_start_httpd(raop, &port);
            raop_set_port(raop, port);
            dnssd = dnssd_init(name, static_cast<int>(strlen(name)), reinterpret_cast<const char *>(hw), 6, 0, &dnssd_error);
            if (!dnssd || dnssd_error) {
                result = -4;
            } else {
                set_features(dnssd);
                raop_set_dnssd(raop, dnssd);
                result = (dnssd_register_raop(dnssd, port) || dnssd_register_airplay(dnssd, port)) ? -5 : port;
            }
        }
    }
    env->ReleaseStringUTFChars(jname, name);
    env->ReleaseStringUTFChars(jkeyfile, keyfile);
    if (result < 0) LOGE("AirPlay start failed: %d (dnssd %d)", result, dnssd_error);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_tcltv_sahne_airplay_AirPlayNative_disconnect(JNIEnv *, jobject) {
    if (raop) raop_remove_known_connections(raop);
}

extern "C" JNIEXPORT void JNICALL
Java_com_tcltv_sahne_airplay_AirPlayNative_stop(JNIEnv *env, jobject) {
    if (dnssd) {
        dnssd_unregister_raop(dnssd);
        dnssd_unregister_airplay(dnssd);
    }
    if (raop) {
        raop_destroy(raop);
        raop = nullptr;
    }
    if (dnssd) {
        dnssd_destroy(dnssd);
        dnssd = nullptr;
    }
    if (listener) {
        env->DeleteGlobalRef(listener);
        listener = nullptr;
    }
}
