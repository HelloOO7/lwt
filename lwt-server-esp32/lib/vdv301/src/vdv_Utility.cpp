#include "vdv_Utility.h"

#include "FNVHash.h"
#include <cstring>

namespace vdv301 {

    uint32_t HashResponseWithoutTimestamp(const char* responseXml)
    {
        // this is a very simple approach to remove the timestamp from the response XML
        // it assumes that the timestamp is always in the same format and position
        // and that there are no other timestamps in the XML
        // if the format changes, this function will need to be updated

        const char* timestampStart = strstr(responseXml, "<TimeStamp>");
        if (!timestampStart) {
            return FNV1aHash(std::span<const char>(responseXml, strlen(responseXml)));
        }

        const char* timestampEnd = strstr(timestampStart, "</TimeStamp>");
        timestampEnd += strlen("</TimeStamp>");
        
        auto hash = FNV1aHashInit();
        hash = FNV1aHashUpdate(hash, std::span<const char>(responseXml, timestampStart));
        hash = FNV1aHashUpdate(hash, std::span<const char>(timestampEnd, strlen(timestampEnd)));
        
        return hash;
    }
}