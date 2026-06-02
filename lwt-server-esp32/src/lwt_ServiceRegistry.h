#pragma once

#include <functional>
#include "packet_generated.h"
#include <vector>
#include <utility>
#include "PSRAMContainers.h"

namespace lwt {

    using ResponseStatus = int;

    class OperationResult {
    private:
        ResponseStatus m_Status;
        psram_vector<uint8_t> m_ResponseData;
    public:
        inline OperationResult(ResponseStatus status) : m_Status(status) {}
        inline OperationResult(ResponseStatus status, psram_vector<uint8_t>&& responseData) : m_Status(status), m_ResponseData(std::move(responseData)) {}
        inline OperationResult(psram_vector<uint8_t>&& responseData) : OperationResult(200, std::move(responseData)) {}

        inline ResponseStatus GetStatus() const { return m_Status; }
        inline const psram_vector<uint8_t>& GetResponseData() const { return m_ResponseData; };
    };

    using OperationFunction = std::function<OperationResult(const RequestPacket&)>;

    class ServiceRegistry {
    private:
        size_t m_OperationMin;
        size_t m_OperationMax;
        std::vector<OperationFunction> m_OperationCallbacks;
        std::vector<char> m_OperationRegisteredBitmap;

    public:
        template<typename TOperation>
        ServiceRegistry(TOperation operationMin, TOperation operationMax) :
            m_OperationMin(std::to_underlying(operationMin)),
            m_OperationMax(std::to_underlying(operationMax)),
            m_OperationCallbacks(std::to_underlying(operationMax) - std::to_underlying(operationMin) + 1, NoOpOperation),
            m_OperationRegisteredBitmap(m_OperationCallbacks.size(), false)
        {

        }

        template<typename TOperation>
        size_t GetOperationIndex(TOperation operation) const {
            auto operationIndex = std::to_underlying(operation) - m_OperationMin;
            if (operationIndex >= m_OperationCallbacks.size()) {
                throw std::out_of_range("Operation ID out of range");
            }
            return operationIndex;
        }

        template<typename TOperation, typename TOperationFunc>
        void RegisterServiceCallback(TOperation operation, TOperationFunc&& func) {
            m_OperationCallbacks[GetOperationIndex(operation)] = std::forward<TOperationFunc>(func);
            m_OperationRegisteredBitmap[GetOperationIndex(operation)] = true;
        }

        template<typename... Services>
        void RegisterServices(Services&&... services) {
            (services.Register(*this), ...);
        }

        template<typename TOperation>
        bool IsServiceRegistered(TOperation operation) const {
            if (operation < m_OperationMin || operation > m_OperationMax) {
                return false;
            }
            return m_OperationRegisteredBitmap[GetOperationIndex(operation)];
        }

        template<typename TOperation>
        const OperationFunction& GetService(TOperation operation) const {
            return m_OperationCallbacks[GetOperationIndex(operation)];
        }
    private:
        static OperationResult NoOpOperation(const RequestPacket& request);
    };
}