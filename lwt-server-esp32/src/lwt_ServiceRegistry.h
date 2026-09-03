#pragma once

#include <functional>
#include "packet_generated.h"
#include <vector>
#include <utility>
#include "PSRAMContainers.h"
#include "lwt_CertRole.h"
#include "CommonTypes.h"

namespace lwt {

    using ResponseStatus = int;

    class OperationResult {
    private:
        ResponseStatus m_Status;
        ByteVector m_ResponseData;
    public:
        inline OperationResult(ResponseStatus status) : m_Status(status) {}
        inline OperationResult(ResponseStatus status, ByteVector&& responseData) : m_Status(status), m_ResponseData(std::move(responseData)) {}
        inline OperationResult(ByteVector&& responseData) : OperationResult(200, std::move(responseData)) {}

        inline ResponseStatus GetStatus() const { return m_Status; }
        inline const ByteVector& GetResponseData() const { return m_ResponseData; };
    };

    using OperationFunction = std::function<OperationResult(const RequestPacket&)>;

    class ServiceRegistry {
    private:
        struct Registration {
            OperationFunction m_Callback;
            CertRole m_RequiredRole{ CertRole::NONE };
        };

        size_t m_OperationMin;
        size_t m_OperationMax;
        std::vector<Registration> m_Operations;
        std::vector<char> m_OperationRegisteredBitmap;

    public:
        template<typename TOperation>
        ServiceRegistry(TOperation operationMin, TOperation operationMax) :
            m_OperationMin(std::to_underlying(operationMin)),
            m_OperationMax(std::to_underlying(operationMax)),
            m_Operations(std::to_underlying(operationMax) - std::to_underlying(operationMin) + 1, { NoOpOperation, CertRole::NONE }),
            m_OperationRegisteredBitmap(m_Operations.size(), false)
        {

        }

        template<typename TOperation>
        size_t GetOperationIndex(TOperation operation) const {
            auto operationIndex = std::to_underlying(operation) - m_OperationMin;
            if (operationIndex >= m_Operations.size()) {
                throw std::out_of_range("Operation ID out of range");
            }
            return operationIndex;
        }

        template<typename TOperation, typename TOperationFunc>
        void RegisterServiceCallback(TOperation operation, TOperationFunc&& func, CertRole requiredRole = CertRole::NONE) {
            m_Operations[GetOperationIndex(operation)].m_Callback = std::forward<TOperationFunc>(func);
            m_Operations[GetOperationIndex(operation)].m_RequiredRole = requiredRole;
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
            return m_Operations[GetOperationIndex(operation)].m_Callback;
        }

        template<typename TOperation>
        bool CheckOperationAccess(TOperation operation, CertRole ownedRoles) const {
            auto requiredRoleMask = m_Operations[GetOperationIndex(operation)].m_RequiredRole;
            if (requiredRoleMask == CertRole::NONE) {
                return true;
            }
            return (ownedRoles & requiredRoleMask) != CertRole::NONE;
        }

    private:
        static OperationResult NoOpOperation(const RequestPacket& request);
    };
}