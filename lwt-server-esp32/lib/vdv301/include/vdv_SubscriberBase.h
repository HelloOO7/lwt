#pragma once

#include <type_traits>
#include <vector>
#include <functional>
#include <mutex>

namespace vdv301 {

    template<typename D>
    class SubscriberObserver
    {
    public:
        virtual ~SubscriberObserver() = default;

        virtual void OnDataChanged(const D* result) = 0;
    };

    class SubscriberBase
    {
    private:
        struct ObserverEntry {
            size_t m_Tag;
            SubscriberObserver<void>* m_Observer;
        };

        std::vector<ObserverEntry> m_Observers;
        std::mutex m_ObserversMutex;

    public:
        SubscriberBase();
        virtual ~SubscriberBase();

    protected:
        template<typename TagT>
        static constexpr bool can_be_tag_v = std::is_integral_v<TagT> || std::is_enum_v<TagT>;

        template<typename TagT, typename DataT>
            requires can_be_tag_v<TagT>
        void AddObserver(TagT tag, SubscriberObserver<DataT>& observer)
        {
            AddObserverImpl(static_cast<size_t>(tag), reinterpret_cast<SubscriberObserver<void>&>(observer));
        }

        template<typename TagT, typename DataT>
            requires can_be_tag_v<TagT>
        void RemoveObserver(TagT tag, SubscriberObserver<DataT>& observer)
        {
            RemoveObserverImpl(static_cast<size_t>(tag), reinterpret_cast<SubscriberObserver<void>&>(observer));
        }

        template<typename TagT, typename DataT>
            requires can_be_tag_v<TagT>
        void NotifyObservers(TagT tag, const DataT* result)
        {
            NotifyObserversImpl(static_cast<size_t>(tag), reinterpret_cast<const void*>(result));
        }

        template<typename TagT>
            requires can_be_tag_v<TagT>
        void InvalidateObservers(TagT tag)
        {
            NotifyObservers(tag, nullptr);
        }

    private:
        void AddObserverImpl(size_t tag, SubscriberObserver<void>& observer);
        void RemoveObserverImpl(size_t tag, SubscriberObserver<void>& observer);
        void NotifyObserversImpl(size_t tag, const void* result);
    };
}