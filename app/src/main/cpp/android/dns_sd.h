/*
 * A small stand-in for Apple's dns_sd.h. Android has no Bonjour daemon, so services are not announced from C:
 * DNSServiceRegister hands the name, type, port and TXT record to a hook that Kotlin turns into an NsdManager call.
 */
#ifndef MATINEE_DNS_SD_H
#define MATINEE_DNS_SD_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct _DNSServiceRef_t *DNSServiceRef;
typedef uint32_t DNSServiceFlags;
typedef int32_t DNSServiceErrorType;

typedef union _TXTRecordRef_t {
    char PrivateData[16];
    char *ForceNaturalAlignment;
} TXTRecordRef;

#define kDNSServiceErr_NoError 0
#define kDNSServiceErr_Unknown -65537

typedef void (*DNSServiceRegisterReply)(DNSServiceRef sdRef, DNSServiceFlags flags, DNSServiceErrorType errorCode,
                                        const char *name, const char *regtype, const char *domain, void *context);

DNSServiceErrorType DNSServiceRegister(DNSServiceRef *sdRef, DNSServiceFlags flags, uint32_t interfaceIndex,
                                       const char *name, const char *regtype, const char *domain, const char *host,
                                       uint16_t port, uint16_t txtLen, const void *txtRecord,
                                       DNSServiceRegisterReply callBack, void *context);
void DNSServiceRefDeallocate(DNSServiceRef sdRef);
int DNSServiceRefSockFD(DNSServiceRef sdRef);
DNSServiceErrorType DNSServiceProcessResult(DNSServiceRef sdRef);

void TXTRecordCreate(TXTRecordRef *txtRecord, uint16_t bufferLen, void *buffer);
void TXTRecordDeallocate(TXTRecordRef *txtRecord);
DNSServiceErrorType TXTRecordSetValue(TXTRecordRef *txtRecord, const char *key, uint8_t valueSize, const void *value);
uint16_t TXTRecordGetLength(const TXTRecordRef *txtRecord);
const void *TXTRecordGetBytesPtr(const TXTRecordRef *txtRecord);

/* Called with registered = 1 when a service should be announced and 0 when it should be withdrawn. */
typedef void (*MatineeServiceHook)(int registered, const char *name, const char *regtype, uint16_t port,
                                   const uint8_t *txt, uint16_t txtLen);
void matinee_set_service_hook(MatineeServiceHook hook);

#ifdef __cplusplus
}
#endif

#endif
