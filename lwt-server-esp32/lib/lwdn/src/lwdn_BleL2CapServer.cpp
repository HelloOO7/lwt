#include "lwdn_BleL2CapServer.h"

#include "errno.h"
#include "esp_err.h"
#include "esp_log.h"
#include "host/ble_hs.h"
#include "host/ble_l2cap.h"
#include "esp_bt.h"
#include <cassert>
#include "PSRAMAllocator.h"

namespace lwdn {

    static constexpr const char* TAG = "BleL2CapServer";

    BleL2CapServer::BleL2CapServer(uint16_t psm, uint16_t mtu) :
        m_Psm{ psm },
        m_Mtu{ mtu }
    {
        int err = ble_l2cap_create_server(psm, mtu, L2CapEventCallback, this);
        assert(err == 0);
    }

    BleL2CapServer::~BleL2CapServer()
    {
        std::lock_guard lock(m_GlobalEventLock);
        /*ble_l2cap_remove_server(m_Psm);
        assert(m_Channels.empty());
        m_SocketAvailable.notify_all();*/
        // current nimBLE version in esp idf does not support terminating a server.
        // this will eventually fix itself, as latest nimBLE already has this resolved.
        assert(0);
    }

    BleL2CapServer::Channel::Channel(SocketHandle socketHandle, uint16_t connHandle, uint16_t mtu, uint16_t chunkSize) :
        m_SocketHandle{ socketHandle },
        m_ConnHandle{ connHandle },
        m_Mtu{ mtu },
        m_ChunkSize{ chunkSize },
        m_ChunkSizeWithOverhead{ (uint16_t)(chunkSize + sizeof(os_mbuf) + sizeof(os_mbuf_pkthdr)) },
        m_ChunkCountPerMbuf{ static_cast<uint16_t>((mtu + chunkSize - 1) / chunkSize) },
        m_MbufSetCount{ 3 /* tx, rx1, rx2 (bug workaround) */ },
        m_ChunkCount{ (uint16_t)(m_MbufSetCount * m_ChunkCountPerMbuf) },
        m_MemBufData(OS_MEMPOOL_SIZE(m_ChunkCount, m_ChunkSizeWithOverhead))
    {
        if (os_mempool_init(&m_MemPool, m_ChunkCount, m_ChunkSizeWithOverhead, m_MemBufData.data(), "L2CAPChanMemPool")) {
            ESP_ERROR_CHECK(ESP_ERR_NO_MEM);
        }
        if (os_mbuf_pool_init(&m_MbufPool, &m_MemPool, m_ChunkSizeWithOverhead, m_ChunkCount)) {
            ESP_ERROR_CHECK(ESP_ERR_NO_MEM);
        }
        m_MainRxBuf = os_mbuf_get_pkthdr(&m_MbufPool, 0);
    }

    uint16_t BleL2CapServer::GetPsm() const
    {
        return m_Psm;
    }

    uint16_t BleL2CapServer::GetMtu() const
    {
        return m_Mtu;
    }

    std::unique_ptr<Socket> BleL2CapServer::Accept()
    {
        std::unique_lock lock(m_GlobalEventLock);

        SocketHandle handle = m_NextAcceptedSocketHandle;

        while (true) {
            auto it = std::find_if(m_Channels.begin(), m_Channels.end(), [handle](auto&& channel) { return channel->m_SocketHandle >= handle; });
            if (it != m_Channels.end()) {
                handle = (*it)->m_SocketHandle;
                break;
            }
            else {
                m_SocketAvailable.wait(lock);
                if (m_Channels.empty()) {
                    // server was closed while waiting
                    return nullptr;
                }
            }
            handle = m_NextAcceptedSocketHandle;
        }
        m_NextAcceptedSocketHandle = handle + 1;

        return std::unique_ptr<BleL2CapSocket>(new BleL2CapSocket(this, m_Channels.back())); // use new here to access private constructor
    }

    int BleL2CapServer::ReadChannel(Channel* channel, void* buffer, size_t len, size_t* receivedLen, size_t timeout)
    {
        std::unique_lock lock(m_GlobalEventLock);

        ESP_LOGI(TAG, "ReadChannel: conn_handle=%d, requested_len=%d, timeout=%d", channel->m_ConnHandle, len, timeout);

        if (OS_MBUF_PKTLEN(channel->m_MainRxBuf) == 0) {
            if (channel->m_Closed) {
                return ECONNRESET;
            }
            if (timeout == 0) {
                return EWOULDBLOCK;
            }
            else {
                auto wakeupCond = [&channel] { return OS_MBUF_PKTLEN(channel->m_MainRxBuf) > 0 || channel->m_Closed; };
                if (timeout == SIZE_MAX) {
                    channel->m_RxBufAvailable.wait(lock, wakeupCond);
                }
                else {
                    channel->m_RxBufAvailable.wait_for(lock, std::chrono::milliseconds(timeout), wakeupCond);
                }
                if (OS_MBUF_PKTLEN(channel->m_MainRxBuf) == 0) {
                    return channel->m_Closed ? ECONNRESET : ETIMEDOUT;
                }
            }
        }

        size_t toCopy = std::min<size_t>(len, OS_MBUF_PKTLEN(channel->m_MainRxBuf));
        assert(os_mbuf_copydata(channel->m_MainRxBuf, 0, toCopy, buffer) == 0);
        os_mbuf_adj(channel->m_MainRxBuf, toCopy);
        channel->m_MainRxBuf = os_mbuf_trim_front(channel->m_MainRxBuf);
        if (receivedLen) {
            *receivedLen = toCopy;
        }

        return 0;
    }

    int BleL2CapServer::WriteChannel(Channel* channel, const void* data, size_t len, size_t* sentLen)
    {
        std::unique_lock lock(m_GlobalEventLock);

        ESP_LOGI(TAG, "WriteChannel: conn_handle=%d, data_len=%d", channel->m_ConnHandle, len);

        if (channel->m_Closed) {
            return ECONNRESET;
        }

        if (len > m_Mtu) {
            ESP_LOGE(TAG, "WriteChannel: data size %d exceeds MTU %d", len, m_Mtu);
            return EMSGSIZE;
        }

        os_mbuf* mbuf = os_mbuf_get_pkthdr(&channel->m_MbufPool, 0);
        if (!mbuf) {
            return ENOMEM;
        }
        int err = os_mbuf_copyinto(mbuf, 0, data, len);
        if (err) {
            os_mbuf_free(mbuf);
            return err;
        }
        channel->m_TxIssued = true;

        while (true) {
            if (channel->m_Closed) {
                err = ECONNRESET;
                break;
            }
            err = ble_l2cap_send(channel->m_Chan, mbuf);
            if (err == BLE_HS_ESTALLED) {
                channel->m_TxUnstalled.wait(lock);
            }
            else {
                break;
            }
        }

        if (err == 0) {
            if (sentLen) {
                *sentLen = len;
            }
        }
        else {
            // if an error occured, free the mbuf. otherwise, ownership of mbuf is transferred to nimble stack, which will free it when done.
            os_mbuf_free_chain(mbuf);
        }

        return err;
    }

    int BleL2CapServer::HandleL2CapEvent(ble_l2cap_event* event)
    {
        std::lock_guard lock(m_GlobalEventLock);

        switch (event->type) {
        case BLE_L2CAP_EVENT_COC_CONNECTED:
        {
            ESP_LOGI(TAG, "Channel connected: conn_handle=%d, status=%d", event->connect.conn_handle, event->connect.status);
            Channel* channel = FindChannel(event->connect.conn_handle, false);
            if (!channel) {
                return BLE_HS_ENOENT;
            }
            else {
                channel->m_Chan = event->connect.chan;
            }
            break;
        }
        case BLE_L2CAP_EVENT_COC_DISCONNECTED:
        {
            ESP_LOGI(TAG, "Channel disconnected: conn_handle=%d", event->disconnect.conn_handle);
            auto* channel = FindChannel(event->disconnect.conn_handle, false);
            if (channel) {
                channel->m_Closed = true;
                channel->m_RxBufAvailable.notify_all();
                channel->m_TxUnstalled.notify_all();
            }
            // this erases the channel from the list. sockets may still hold it via shared ptr.
            OnChannelClosed(event->disconnect.conn_handle);
            break;
        }
        case BLE_L2CAP_EVENT_COC_ACCEPT:
        {
            ESP_LOGI(TAG, "Channel accept: conn_handle=%d, peer sdu size=%d", event->accept.conn_handle, event->accept.peer_sdu_size);
            Channel* channel = FindChannel(event->accept.conn_handle, true);
            if (!channel) {
                return BLE_HS_ENOMEM_EVT;
            }
            else {
                m_SocketAvailable.notify_one();
                channel->m_Chan = event->accept.chan;
                ble_l2cap_chan_info channelInfo;
                if (ble_l2cap_get_chan_info(channel->m_Chan, &channelInfo) == 0) {
                    ESP_LOGI(TAG, "Channel info: scid=%d, dcid=%d, our_l2cap_mtu=%d, peer_l2cap_mtu=%d, psm=%d, our_coc_mtu=%d, peer_coc_mtu=%d",
                        channelInfo.scid, channelInfo.dcid, channelInfo.our_l2cap_mtu, channelInfo.peer_l2cap_mtu,
                        channelInfo.psm, channelInfo.our_coc_mtu, channelInfo.peer_coc_mtu);
                }
                os_mbuf* rxbuf = os_mbuf_get_pkthdr(&channel->m_MbufPool, 0);
                channel->m_TempRxBuf = rxbuf;
                return ble_l2cap_recv_ready(event->accept.chan, rxbuf);
            }
            break;
        }
        case BLE_L2CAP_EVENT_COC_DATA_RECEIVED:
        {
            ESP_LOGI(TAG, "Data received: conn_handle=%d, len=%d", event->receive.conn_handle, OS_MBUF_PKTLEN(event->receive.sdu_rx));
            Channel* channel = FindChannel(event->receive.conn_handle, false);
            if (!channel) {
                ESP_LOGE(TAG, "Channel not registered: conn_handle=%d", event->receive.conn_handle);
                return BLE_HS_ENOENT;
            }
            else {
                channel->m_Chan = event->receive.chan;

                assert(event->receive.sdu_rx == channel->m_TempRxBuf);

                channel->m_MainRxBuf = os_mbuf_pack_chains(channel->m_MainRxBuf, channel->m_TempRxBuf);

                channel->m_TempRxBuf = os_mbuf_get_pkthdr(&channel->m_MbufPool, 0);
                if (!channel->m_TempRxBuf) {
                    ESP_LOGE(TAG, "Failed to allocate new mbuf for receiving: conn_handle=%d", event->receive.conn_handle);
                    CloseChannelNoLock(channel);
                    return ENOMEM;
                }

                int err = ble_l2cap_recv_ready(channel->m_Chan, channel->m_TempRxBuf);

                channel->m_RxBufAvailable.notify_one();

                if (err) {
                    ESP_LOGE(TAG, "recv_ready failed: conn_handle=%d, err=%d", event->receive.conn_handle, err);
                }

                return err;
            }
            break;
        }
        case BLE_L2CAP_EVENT_COC_PEER_RECONFIGURED:
        case BLE_L2CAP_EVENT_COC_RECONFIG_COMPLETED:
        {
            ESP_LOGI(TAG, "Channel reconfigured: conn_handle=%d", event->reconfigured.conn_handle);
            Channel* channel = FindChannel(event->reconfigured.conn_handle, false);
            if (!channel) {
                return BLE_HS_ENOENT;
            }
            else {
                channel->m_Chan = event->reconfigured.chan;
            }
        }
        case BLE_L2CAP_EVENT_COC_TX_UNSTALLED:
        {
            ESP_LOGI(TAG, "Channel tx unstalled: conn_handle=%d", event->tx_unstalled.conn_handle);
            Channel* channel = FindChannel(event->tx_unstalled.conn_handle, false);
            if (channel) {
                channel->m_TxUnstalled.notify_one();
            }
            else {
                return BLE_HS_ENOENT;
            }
            break;
        }
        default:
            break;
        }
        return 0;
    }

    int BleL2CapServer::L2CapEventCallback(ble_l2cap_event* event, void* arg)
    {
        BleL2CapServer* server = static_cast<BleL2CapServer*>(arg);
        return server->HandleL2CapEvent(event);
    }

    BleL2CapServer::Channel* BleL2CapServer::FindChannel(uint16_t connHandle, bool createIfNotFound)
    {
        for (auto& channel : m_Channels) {
            if (channel->m_ConnHandle == connHandle) {
                return channel.get();
            }
        }
        if (createIfNotFound) {
            try {
                m_Channels.emplace_back(std::unique_ptr<Channel>(new Channel(m_NextSocketHandle++, connHandle, m_Mtu, 256)));
                return m_Channels.back().get();
            }
            catch (const std::bad_alloc&) {
                return nullptr;
            }
        }
        return nullptr;
    }

    BleL2CapServer::Channel* BleL2CapServer::FindChannelBySocket(SocketHandle socketHandle)
    {
        for (auto& channel : m_Channels) {
            if (channel->m_SocketHandle == socketHandle) {
                return channel.get();
            }
        }
        return nullptr;
    }

    void BleL2CapServer::OnChannelClosed(uint16_t connHandle)
    {
        std::erase_if(m_Channels, [connHandle](auto&& channel) { return channel->m_ConnHandle == connHandle; });
    }

    int BleL2CapServer::CloseChannel(Channel* channel) {
        std::lock_guard lock(m_GlobalEventLock);

        return CloseChannelNoLock(channel);
    }

    int BleL2CapServer::CloseChannelNoLock(Channel* channel)
    {
        if (channel->m_Closed) {
            return EALREADY;
        }
        channel->m_Closed = true;

        //ESP_LOGI(TAG, "Closing channel from socket dtor: conn_handle=%d", channel->m_ConnHandle);

        // OnChannelClosed will be called asynchonously in event callback later

        // bug: can not actually called disconnect, as it causes race conditions within nimBLE when a TX has not yet finished.
        // we will have to wait for the client to disconnect on their end.
        //if (!channel->m_TxIssued) {
        return ble_l2cap_disconnect(channel->m_Chan);
        //}

        //return 0;
    }

    BleL2CapSocket::BleL2CapSocket(BleL2CapServer* server, std::shared_ptr<BleL2CapServer::Channel> channel) :
        m_Server{ server },
        m_Channel{ channel }
    {
    }

    BleL2CapSocket::~BleL2CapSocket() {
        m_Server->CloseChannel(m_Channel.get());
    }

    int BleL2CapSocket::Write(const void* data, size_t len, size_t* sentLen)
    {
        return m_Server->WriteChannel(m_Channel.get(), data, len, sentLen);
    }

    int BleL2CapSocket::Read(void* buffer, size_t len, size_t* receivedLen, size_t timeout)
    {
        return m_Server->ReadChannel(m_Channel.get(), buffer, len, receivedLen, timeout);
    }
}