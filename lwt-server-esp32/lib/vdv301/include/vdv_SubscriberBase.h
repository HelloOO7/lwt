#pragma once

#include <type_traits>
#include <vector>
#include <functional>
#include <mutex>

namespace vdv301 {

    class SubscriberBase
    {
    public:
        SubscriberBase();
        virtual ~SubscriberBase();
    };
}