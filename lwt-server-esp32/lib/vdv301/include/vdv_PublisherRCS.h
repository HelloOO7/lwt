#pragma once

#include "vdv_PublisherHttp.h"
#include <string>
#include <optional>
#include "IBIS_IP_RemoteControlService_V2_3CZ1_0.hpp"

namespace vdv301 {

    namespace RCS = IBIS_IP_RemoteControlService_V2_3CZ1_0;

    class PublisherRCS : public PublisherHttp
    {
    public:
        enum class Operation : OperationIDType {
            AllData = DefineOp(0),
        };

    public:
        PublisherRCS(ServiceDiscovery& sd);
        ~PublisherRCS();

        void StartRazzia();
        void StopRazzia();

    private:
        void SendSignal(RCS::RemoteControlMessageTypeEnumeration type, std::optional<std::string> parameter = std::nullopt);

    protected:
        virtual std::string GetOperationName(OperationIDType operation) const override;
    };
}