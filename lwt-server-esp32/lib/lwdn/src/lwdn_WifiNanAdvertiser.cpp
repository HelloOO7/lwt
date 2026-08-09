#include "lwdn_WifiNanAdvertiser.h"

namespace lwdn {

    WifiNanAdvertiser::WifiNanAdvertiser(WifiNanPublisher& publisher) :
        m_Publisher{ publisher }
    {
        m_Publisher.RegisterMessageHandler(
            [this](WifiNanPublisher::Message& message) {
                if (message.empty() || (message.size() == 1 && message[0] == (uint8_t)-1)) {
                    // for Android, we are unable to send an empty message, so -1 is the agreed-upon placeholder
                    message.Reply(m_DynamicSSI);
                }
            }
        );
    }

    void WifiNanAdvertiser::Start() {
        m_Publisher.Publish();
    }

    void WifiNanAdvertiser::Stop() {
        m_Publisher.Cancel();
    }

    bool WifiNanAdvertiser::IsAdvertising() const {
        return m_Publisher.IsPublishing();
    }

    size_t WifiNanAdvertiser::GetMaxAdvDataSize() const {
        return ESP_WIFI_MAX_SVC_SSI_LEN - LinkAddress{}.size();
    }

    bool WifiNanAdvertiser::SetLwdnAdvData(const std::span<const uint8_t>& data) {
        auto macAddress = GetLinkAdapter()->GetLinkAddress();
        m_DynamicSSI.resize(macAddress.size() + data.size());
        std::copy(macAddress.begin(), macAddress.end(), m_DynamicSSI.begin());
        std::copy(data.begin(), data.end(), m_DynamicSSI.begin() + macAddress.size());
        return true;
    }
}