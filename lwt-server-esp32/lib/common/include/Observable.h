#pragma once

#include <vector>
#include <mutex>

template<typename D>
class Observer
{
public:
    virtual ~Observer() = default;

    virtual void OnChanged(const D* result) = 0;
};

template<typename T>
class Observable
{
private:
    std::vector<Observer<T>*> m_Observers;
    std::mutex m_ObserversMutex;

public:
    Observable() = default;
    virtual ~Observable() = default;

protected:
    void AddObserver(Observer<T>& observer)
    {
        std::lock_guard lock(m_ObserversMutex);
        m_Observers.push_back(&observer);
    }

    void RemoveObserver(Observer<T>& observer)
    {
        std::lock_guard lock(m_ObserversMutex);
        m_Observers.erase(std::remove(m_Observers.begin(), m_Observers.end(), &observer), m_Observers.end());
    }

    void NotifyObservers(const T* result)
    {
        std::lock_guard lock(m_ObserversMutex);
        for (auto observer : m_Observers)
        {
            observer->OnChanged(result);
        }
    }

    void InvalidateObservers()
    {
        NotifyObservers(nullptr);
    }
};