#include "TimerProc.h"

TimerProc::TimerProc(const Callback& callback, uint64_t periodUs, Type type) :
    m_Callback(callback),
    m_PeriodUs(periodUs),
    m_Type(type)
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
}

TimerProc::~TimerProc() {
    Stop();
    ESP_ERROR_CHECK(esp_timer_delete(m_TimerHandle));
}

bool TimerProc::Start() {
    esp_err_t err;
    if (m_Type == Type::ONESHOT) {
        err = esp_timer_start_once(m_TimerHandle, m_PeriodUs);
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

void TimerProc::Restart() {
    esp_err_t err = esp_timer_restart(m_TimerHandle, m_PeriodUs);
    if (err == ESP_ERR_INVALID_STATE) {
        // timer was not running, start it
        Start();
    }
    else if (err != ESP_OK) {
        ESP_ERROR_CHECK(err);
    }
}