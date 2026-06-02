#include "flatbuffer_util.h"

class PSRAMFlatBufferAllocator : public flatbuffers::Allocator {
public:
    uint8_t* allocate(size_t size) override {
        return static_cast<uint8_t*>(heap_caps_malloc(size, MALLOC_CAP_SPIRAM));
    }

    void deallocate(uint8_t* p, size_t size) override {
        heap_caps_free(p);
    }
};

PSRAMFlatBufferAllocator g_PSRAMFlatBufferAllocator;

flatbuffers::FlatBufferBuilder PSRAMFlatBufferBuilder(size_t initialSize)
{
    return flatbuffers::FlatBufferBuilder(initialSize, &g_PSRAMFlatBufferAllocator, false);
}