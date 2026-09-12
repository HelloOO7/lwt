#include "TimerProc.h"

TimerProc::TimerProc(const Callback& callback, uint64_t periodUs, Type type, bool isWhen) :
    m_Callback(callback),
    m_PeriodUs(periodUs),
    m_Type(type),
    m_IsWhen(isWhen)
{
    esp_timer_create_args_t timerArgs{};
    timerArgs.callback =
        [](void* arg)
        {
            auto* self = static_cast<TimerProc*>(arg);
            if (self->m_Callback) {
                self->m_Callback();
            }
        };
    timerArgs.arg = this;
    timerArgs.dispatch_method = ESP_TIMER_TASK;
    timerArgs.name = "TimerProc";

    ESP_ERROR_CHECK(esp_timer_create(&timerArgs, &m_TimerHandle));

    if (isWhen && m_PeriodUs != 0) {
        Start();
    }
}

TimerProc::TimerProc(const Callback& callback, uint64_t periodUs, Type type) :
    TimerProc(callback, periodUs, type, false)
{
}

TimerProc::TimerProc(const Callback& callback, uint64_t whenUs) :
    TimerProc(callback, whenUs, Type::ONESHOT, true)
{
}

TimerProc::~TimerProc() {
    Stop();
    ESP_ERROR_CHECK(esp_timer_delete(m_TimerHandle));
}

bool TimerProc::Start() {
    esp_err_t err;
    if (m_Type == Type::ONESHOT) {
        if (m_IsWhen) {
            err = esp_timer_start_once_at(m_TimerHandle, m_PeriodUs);
        }
        else {
            err = esp_timer_start_once(m_TimerHandle, m_PeriodUs);
        }
    }
    else {
        err = esp_timer_start_periodic(m_TimerHandle, m_PeriodUs);
    }
    if (err == ESP_ERR_INVALID_STATE) {
        return false; // already started
    }
    else if (err != ESP_OK) {
        ESP_ERROR_CHECK(err);
    }
    return true;
}

void TimerProc::Stop() {
    esp_err_t err = esp_timer_stop(m_TimerHandle);
    if (err != ESP_OK && err != ESP_ERR_INVALID_STATE) {
        ESP_ERROR_CHECK(err);
    }
}

void TimerProc::Restart(uint64_t newPeriodWhen) {
    m_PeriodUs = newPeriodWhen != 0 ? newPeriodWhen : m_PeriodUs;
    esp_err_t err;
    if (m_IsWhen) {
        err = esp_timer_restart_at(m_TimerHandle, 0, m_PeriodUs);
    }
    else {
        err = esp_timer_restart(m_TimerHandle, m_PeriodUs);
    }
    if (err == ESP_ERR_INVALID_STATE) {
        // timer was not running, start it
        Start();
    }
    else if (err != ESP_OK) {
        ESP_ERROR_CHECK(err);
    }
}