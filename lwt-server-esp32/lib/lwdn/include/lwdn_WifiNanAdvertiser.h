#pragma once

#include <string>

#include "lwdn_Advertiser.h"
#include "esp_nan.h"
#include "lwdn_WifiNanLink.h"
#include "lwdn_WifiNanPublisher.h"
#include "lwdn_ServerSocket.h"
#include "PSRAMContainers.h"
#include "CommonTypes.h"

namespace lwdn {

    class WifiNanAdvertiser : public Advertiser
    {
    private:
        WifiNanPublisher& m_Publisher;
        
        ByteVector m_DynamicSSI;

    public:
        WifiNanAdvertiser(WifiNanPublisher& publisher);

        virtual void Start() override;
        virtual void Stop() override;
        virtual bool IsAdvertising() const override;

        virtual size_t GetMaxAdvDataSize() const override;
        virtual bool SetLwdnAdvData(const ByteSpan& data) override;

        virtual LinkAdapter* GetLinkAdapter() const override { return &WIFI_NAN_ADAPTER; }
    };
}