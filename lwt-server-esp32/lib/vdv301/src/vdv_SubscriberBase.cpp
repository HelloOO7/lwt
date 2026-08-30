#include "vdv_SubscriberBase.h"

#include "esp_log.h"

namespace vdv301
{
    static constexpr const char* TAG = "SubscriberBase";

    SubscriberBase::SubscriberBase(ServiceDiscovery& sd) :
        m_SD(sd)
    {

    }

    SubscriberBase::~SubscriberBase()
    {

    }

    void SubscriberBase::StartBrowse(const ServiceDiscovery::Query& serviceQuery)
    {
        std::lock_guard lock(m_CommMutex);

        m_SDHandle = m_SD.StartBrowse(
            serviceQuery,
            [this](const ServiceDiscovery::ResultSetAccessor& results) {
                std::lock_guard lock(m_CommMutex);

                const ServiceDiscovery::Result* result = results.GetAnyResult();
                if (result) {
                    ESP_LOGI(TAG, "Service query matched: instance=%s host=%s port=%u", result->GetInstanceName().c_str(), result->GetHostName().c_str(), result->GetPort());
                    auto sdh = m_SDHandle.load();
                    if (!sdh) {
                        // called after StopBrowse
                        return;
                    }
                    HandleServiceDiscovered(*result);
                }
                else {
                    ESP_LOGI(TAG, "Service query lost");
                    HandleServiceLost();
                }
            }
        );
    }

    void SubscriberBase::StopBrowse()
    {
        // must not lock here, otherwise we would deadlock
        // (subscriber mutex -> SD mutex vs. SD mutex -> subscriber mutex in HandleServiceDiscovered)
        // fortunately we do not need to lock, as destructor/constructor can not be called concurrently
        auto sdh = m_SDHandle.exchange(0);
        if (sdh) {
            m_SD.StopBrowse(sdh);
        }
    }
}