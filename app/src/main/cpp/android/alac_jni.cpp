// Decodes AirPlay's ALAC music frames to 16-bit stereo PCM, because Android has no ALAC decoder of its own.
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>

#include "ALACBitUtilities.h"
#include "ALACDecoder.h"

namespace {

// 44.1 kHz, 16-bit, stereo, 352 samples per frame: the format AirPlay announces for ALAC.
const uint8_t kMagicCookie[] = {0x00, 0x00, 0x00, 0x24, 0x61, 0x6c, 0x61, 0x63, 0x00, 0x00, 0x00, 0x00,
                                0x00, 0x00, 0x01, 0x60, 0x00, 0x10, 0x28, 0x0a, 0x0e, 0x02, 0x00, 0xff,
                                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xac, 0x44};
const uint32_t kSamplesPerFrame = 352;
const uint32_t kChannels = 2;

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_tcltv_sahne_airplay_AlacDecoder_create(JNIEnv *, jobject) {
    auto *decoder = new ALACDecoder();
    if (decoder->Init(const_cast<uint8_t *>(kMagicCookie), sizeof(kMagicCookie)) != 0) {
        delete decoder;
        return 0;
    }
    return reinterpret_cast<jlong>(decoder);
}

/** Returns the decoded 16-bit PCM bytes, or null when the frame is damaged. */
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_tcltv_sahne_airplay_AlacDecoder_decode(JNIEnv *env, jobject, jlong handle, jbyteArray frame) {
    auto *decoder = reinterpret_cast<ALACDecoder *>(handle);
    if (!decoder) return nullptr;
    jsize length = env->GetArrayLength(frame);
    jbyte *input = env->GetByteArrayElements(frame, nullptr);
    BitBuffer bits;
    BitBufferInit(&bits, reinterpret_cast<uint8_t *>(input), static_cast<uint32_t>(length));
    uint8_t pcm[kSamplesPerFrame * kChannels * 2];
    uint32_t samples = 0;
    int32_t status = decoder->Decode(&bits, pcm, kSamplesPerFrame, kChannels, &samples);
    env->ReleaseByteArrayElements(frame, input, JNI_ABORT);
    if (status != 0 || samples == 0) return nullptr;
    jsize bytes = static_cast<jsize>(samples * kChannels * 2);
    jbyteArray output = env->NewByteArray(bytes);
    env->SetByteArrayRegion(output, 0, bytes, reinterpret_cast<jbyte *>(pcm));
    return output;
}

extern "C" JNIEXPORT void JNICALL
Java_com_tcltv_sahne_airplay_AlacDecoder_destroy(JNIEnv *, jobject, jlong handle) {
    delete reinterpret_cast<ALACDecoder *>(handle);
}
