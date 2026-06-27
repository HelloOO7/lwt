#include "vdv_SubscriberBase.h"

#include <algorithm>

namespace vdv301
{
    SubscriberBase::SubscriberBase()
    {
    }

    SubscriberBase::~SubscriberBase()
    {
    }

    void SubscriberBase::AddObserverImpl(size_t tag, SubscriberObserver<void>& observer)
    {
        std::lock_guard lock(m_ObserversMutex);

        m_Observers.push_back({ tag, &observer });
    }

    void SubscriberBase::RemoveObserverImpl(size_t tag, SubscriberObserver<void>& observer)
    {
        std::lock_guard lock(m_ObserversMutex);

        std::erase_if(
            m_Observers,
            [tag, &observer](const ObserverEntry& entry) {
                return entry.m_Tag == tag && entry.m_Observer == &observer;
            }
        );
    }

    void SubscriberBase::NotifyObserversImpl(size_t tag, const void* result)
    {
        std::lock_guard lock(m_ObserversMutex);

        for (const auto& entry : m_Observers) {
            if (entry.m_Tag == tag) {
                entry.m_Observer->OnDataChanged(result);
            }
        }
    }
}