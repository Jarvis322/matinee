#include "dns_sd.h"

#include <arpa/inet.h>
#include <stdlib.h>
#include <string.h>

struct _DNSServiceRef_t {
    char *name;
    char *regtype;
    uint16_t port;
    uint8_t *txt;
    uint16_t txt_len;
};

typedef struct {
    uint8_t *data;
    uint16_t len;
    uint16_t cap;
} TxtBuffer;

static MatineeServiceHook service_hook;

void matinee_set_service_hook(MatineeServiceHook hook) {
    service_hook = hook;
}

static TxtBuffer *txt_buffer(const TXTRecordRef *record) {
    return *(TxtBuffer *const *) record->PrivateData;
}

void TXTRecordCreate(TXTRecordRef *txtRecord, uint16_t bufferLen, void *buffer) {
    (void) bufferLen;
    (void) buffer;
    memset(txtRecord, 0, sizeof(*txtRecord));
    *(TxtBuffer **) txtRecord->PrivateData = calloc(1, sizeof(TxtBuffer));
}

void TXTRecordDeallocate(TXTRecordRef *txtRecord) {
    TxtBuffer *buffer = txt_buffer(txtRecord);
    if (buffer) {
        free(buffer->data);
        free(buffer);
    }
    memset(txtRecord, 0, sizeof(*txtRecord));
}

DNSServiceErrorType TXTRecordSetValue(TXTRecordRef *txtRecord, const char *key, uint8_t valueSize, const void *value) {
    TxtBuffer *buffer = txt_buffer(txtRecord);
    size_t key_len = strlen(key);
    size_t entry = key_len + 1 + valueSize;
    if (!buffer || entry > 255) return kDNSServiceErr_Unknown;
    if ((size_t) buffer->len + 1 + entry > buffer->cap) {
        size_t cap = buffer->cap ? buffer->cap * 2u : 256u;
        while (cap < (size_t) buffer->len + 1 + entry) cap *= 2;
        uint8_t *grown = realloc(buffer->data, cap);
        if (!grown) return kDNSServiceErr_Unknown;
        buffer->data = grown;
        buffer->cap = (uint16_t) cap;
    }
    uint8_t *out = buffer->data + buffer->len;
    *out++ = (uint8_t) entry;
    memcpy(out, key, key_len);
    out += key_len;
    *out++ = '=';
    if (valueSize) memcpy(out, value, valueSize);
    buffer->len = (uint16_t) (buffer->len + 1 + entry);
    return kDNSServiceErr_NoError;
}

uint16_t TXTRecordGetLength(const TXTRecordRef *txtRecord) {
    TxtBuffer *buffer = txt_buffer(txtRecord);
    return buffer ? buffer->len : 0;
}

const void *TXTRecordGetBytesPtr(const TXTRecordRef *txtRecord) {
    TxtBuffer *buffer = txt_buffer(txtRecord);
    return buffer ? buffer->data : NULL;
}

DNSServiceErrorType DNSServiceRegister(DNSServiceRef *sdRef, DNSServiceFlags flags, uint32_t interfaceIndex,
                                       const char *name, const char *regtype, const char *domain, const char *host,
                                       uint16_t port, uint16_t txtLen, const void *txtRecord,
                                       DNSServiceRegisterReply callBack, void *context) {
    (void) flags;
    (void) interfaceIndex;
    (void) domain;
    (void) host;
    (void) callBack;
    (void) context;
    if (!service_hook) return kDNSServiceErr_Unknown;
    DNSServiceRef ref = calloc(1, sizeof(*ref));
    if (!ref) return kDNSServiceErr_Unknown;
    ref->name = strdup(name);
    ref->regtype = strdup(regtype);
    ref->port = ntohs(port);
    ref->txt = malloc(txtLen ? txtLen : 1);
    ref->txt_len = txtLen;
    if (txtLen) memcpy(ref->txt, txtRecord, txtLen);
    service_hook(1, ref->name, ref->regtype, ref->port, ref->txt, ref->txt_len);
    *sdRef = ref;
    return kDNSServiceErr_NoError;
}

void DNSServiceRefDeallocate(DNSServiceRef ref) {
    if (!ref) return;
    if (service_hook) service_hook(0, ref->name, ref->regtype, ref->port, ref->txt, ref->txt_len);
    free(ref->name);
    free(ref->regtype);
    free(ref->txt);
    free(ref);
}

int DNSServiceRefSockFD(DNSServiceRef sdRef) {
    (void) sdRef;
    return -1;
}

DNSServiceErrorType DNSServiceProcessResult(DNSServiceRef sdRef) {
    (void) sdRef;
    return kDNSServiceErr_NoError;
}
