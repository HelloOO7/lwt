#include "lwdn_BleL2CapServer.h"

#include "errno.h"
#include "esp_err.h"
#include "host/ble_hs.h"
#include "host/ble_l2cap.h"
#include <cassert>

namespace lwdn {

    BleL2CapServer::BleL2CapServer(uint16_t psm, uint16_t mtu) :
        m_Psm{ psm },
        m_Mtu{ mtu }
    {
        ble_l2cap_create_server(psm, mtu, L2CapEventCallback, this);
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
        m_MbufSetCount{ 3 /* tx, rx1, rx2 */ },
        m_ChunkCount{ (uint16_t)(m_MbufSetCount * m_ChunkCountPerMbuf) },
        m_MemBufData{ std::make_unique<os_membuf_t[]>(OS_MEMPOOL_SIZE(m_ChunkCount, m_ChunkSizeWithOverhead)) }
    {
        if (os_mempool_init(&m_MemPool, m_ChunkCount, m_ChunkSizeWithOverhead, m_MemBufData.get(), "L2CAPChanMemPool")) {
            ESP_ERROR_CHECK(ESP_ERR_NO_MEM);
        }
        if (os_mbuf_pool_init(&m_MbufPool, &m_MemPool, m_ChunkSizeWithOverhead, m_ChunkCount)) {
            ESP_ERROR_CHECK(ESP_ERR_NO_MEM);
        }
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

        return std::unique_ptr<BleL2CapSocket>(new BleL2CapSocket(this, handle)); // use new here to access private constructor
    }

    int BleL2CapServer::ReadChannel(SocketHandle socketHandle, void* buffer, size_t len, size_t* receivedLen, size_t timeout)
    {
        std::unique_lock lock(m_GlobalEventLock);

        Channel* channel = FindChannelBySocket(socketHandle);
        if (!channel) {
            return ECONNRESET;
        }

        if (!channel->m_CurRxBuf) {
            if (timeout == 0) {
                return EWOULDBLOCK;
            }
            else {
                if (timeout == SIZE_MAX) {
                    channel->m_RxBufAvailable.wait(lock, [channel] { return channel->m_CurRxBuf != nullptr; });
                }
                else {
                    channel->m_RxBufAvailable.wait_for(lock, std::chrono::milliseconds(timeout), [channel] { return channel->m_CurRxBuf != nullptr; });
                }
                if (!channel->m_CurRxBuf) {
                    return ETIMEDOUT;
                }
            }
        }

        size_t toCopy = std::min(len, channel->m_CurRxLen - channel->m_CurRxOffset);
        os_mbuf_copydata(channel->m_CurRxBuf, channel->m_CurRxOffset, toCopy, buffer);
        channel->m_CurRxOffset += toCopy;
        if (receivedLen) {
            *receivedLen = toCopy;
        }

        if (channel->m_CurRxOffset >= channel->m_CurRxLen) {
            ble_l2cap_recv_ready(channel->m_Chan, channel->m_CurRxBuf);
            channel->m_CurRxBuf = nullptr;
        }

        return 0;
    }

    int BleL2CapServer::WriteChannel(SocketHandle socketHandle, const void* data, size_t len, size_t* sentLen)
    {
        std::unique_lock lock(m_GlobalEventLock);

        Channel* channel = FindChannelBySocket(socketHandle);
        if (!channel) {
            return ECONNRESET;
        }

        if (len > m_Mtu) {
            return EMSGSIZE;
        }

        os_mbuf* mbuf = os_mbuf_get_pkthdr(&channel->m_MbufPool, 0);
        if (!mbuf) {
            return ENOMEM;
        }
        os_mbuf_copyinto(mbuf, 0, data, len);

        int err;

        while (true) {
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

        os_mbuf_free_chain(mbuf);

        return err;
    }

    int BleL2CapServer::HandleL2CapEvent(ble_l2cap_event* event)
    {
        std::lock_guard lock(m_GlobalEventLock);

        switch (event->type) {
        case BLE_L2CAP_EVENT_COC_CONNECTED:
        {
            Channel* channel = FindChannel(event->connect.conn_handle, true);
            if (!channel) {
                return BLE_HS_ENOMEM_EVT;
            }
            else {
                channel->m_Chan = event->connect.chan;
                m_SocketAvailable.notify_one();
            }
            break;
        }
        case BLE_L2CAP_EVENT_COC_DISCONNECTED:
        {
            OnChannelClosed(event->disconnect.conn_handle);
            break;
        }
        case BLE_L2CAP_EVENT_COC_ACCEPT:
        {
            Channel* channel = FindChannel(event->accept.conn_handle, false);
            if (!channel) {
                return BLE_HS_ENOENT;
            }
            else {
                channel->m_Chan = event->accept.chan;
                os_mbuf* rxbuf = os_mbuf_get_pkthdr(&channel->m_MbufPool, 0);
                return ble_l2cap_recv_ready(event->accept.chan, rxbuf);
            }
            break;
        }
        case BLE_L2CAP_EVENT_COC_DATA_RECEIVED:
        {
            Channel* channel = FindChannel(event->receive.conn_handle, false);
            if (!channel) {
                return BLE_HS_ENOENT;
            }
            else {
                channel->m_Chan = event->receive.chan;
                if (channel->m_CurRxBuf) {
                    // https://github.com/micropython/micropython/blob/44a569b637b56764582c3b652e7bc51b53c0df1d/extmod/nimble/modbluetooth_nimble.c#L1610
                    throw std::runtime_error("Received data while previous data has not been read yet");
                }
                else {
                    channel->m_CurRxBuf = event->receive.sdu_rx;
                    channel->m_CurRxOffset = 0;
                    channel->m_CurRxLen = OS_MBUF_PKTLEN(event->receive.sdu_rx);
                    channel->m_RxBufAvailable.notify_one();
                }
            }
            break;
        }
        case BLE_L2CAP_EVENT_COC_PEER_RECONFIGURED:
        case BLE_L2CAP_EVENT_COC_RECONFIG_COMPLETED:
        {
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

    int BleL2CapServer::CloseChannel(uint16_t connHandle)
    {
        std::lock_guard lock(m_GlobalEventLock);

        Channel* channel = FindChannel(connHandle, false);
        if (!channel) {
            return BLE_HS_ENOENT;
        }
        else {
            // OnChannelClosed will be called asynchonously in event callback later
            return ble_l2cap_disconnect(channel->m_Chan);
        }
    }

    BleL2CapSocket::BleL2CapSocket(BleL2CapServer* server, BleL2CapServer::SocketHandle socketHandle) :
        m_Server{ server },
        m_SocketHandle{ socketHandle }
    {
    }

    BleL2CapSocket::~BleL2CapSocket() {
        m_Server->CloseChannel(m_SocketHandle);
    }

    int BleL2CapSocket::Write(const void* data, size_t len, size_t* sentLen)
    {
        return m_Server->WriteChannel(m_SocketHandle, data, len, sentLen);
    }

    int BleL2CapSocket::Read(void* buffer, size_t len, size_t* receivedLen, size_t timeout)
    {
        return m_Server->ReadChannel(m_SocketHandle, buffer, len, receivedLen, timeout);
    }
}