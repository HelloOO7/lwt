#pragma once

#include "esp_timer.h"
#include <functional>

class TimerProc {
public:
    using Callback = std::function<void()>;

    enum class Type {
        ONESHOT,
        PERIODIC
    };

private:
    esp_timer_handle_t m_TimerHandle;
    Callback m_Callback;
    uint64_t m_PeriodUs;
    Type m_Type;
    bool m_IsWhen;

public:
    TimerProc(const Callback& callback, uint64_t periodUs, Type type);
    TimerProc(const Callback& callback, uint64_t whenUs);
    ~TimerProc();
private:
    TimerProc(const Callback& callback, uint64_t periodUs, Type type, bool isWhen);

public:
    bool Start();
    void Stop();
    void Restart(uint64_t newPeriodWhen = 0);
};