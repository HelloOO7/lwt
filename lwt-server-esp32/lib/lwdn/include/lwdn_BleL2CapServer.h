#pragma once

#include <cstdint>
#include "host/ble_l2cap.h"
#include "os/os_mempool.h"
#include <memory>
#include "lwdn_Socket.h"
#include "lwdn_ServerSocket.h"
#include <vector>
#include <mutex>
#include <condition_variable>
#include "PSRAMContainers.h"

namespace lwdn {

    class BleL2CapSocket;

    class BleL2CapServer : public ServerSocket
    {
        friend class BleL2CapSocket;
    public:
        using SocketHandle = uint32_t;

    private:
        struct Channel {
            SocketHandle m_SocketHandle;
            ble_l2cap_chan* m_Chan;
            bool m_TxIssued{ false };
            bool m_Closed{ false };

            uint16_t m_Mtu;
            uint16_t m_ChunkSize;
            uint16_t m_ChunkSizeWithOverhead;
            uint16_t m_ChunkCountPerMbuf;
            uint16_t m_MbufSetCount;
            uint16_t m_ChunkCount;
            psram_vector<os_membuf_t> m_MemBufData;
            os_mempool m_MemPool;
            os_mbuf_pool m_MbufPool;

            os_mbuf* m_MainRxBuf{ nullptr };
            os_mbuf* m_TempRxBuf{ nullptr };
            std::condition_variable m_RxBufAvailable;

            std::condition_variable m_TxUnstalled;

            Channel(SocketHandle socketHandle, ble_l2cap_chan* nativeChan, uint16_t mtu, uint16_t chunkSize);
        };
    private:
        uint16_t m_Psm;
        uint16_t m_Mtu;

        std::mutex m_GlobalEventLock;
        std::vector<std::shared_ptr<Channel>> m_Channels;

        SocketHandle m_NextSocketHandle{ 1 };
        SocketHandle m_NextAcceptedSocketHandle{ 1 };
        std::condition_variable m_SocketAvailable;

    public:
        BleL2CapServer(uint16_t psm, uint16_t mtu);
        ~BleL2CapServer();

        uint16_t GetPsm() const;
        uint16_t GetMtu() const;

        std::unique_ptr<Socket> Accept() override;

    private:
        Channel* FindChannel(ble_l2cap_chan* nativeChan, bool createIfNotFound = false);
        Channel* FindChannelBySocket(SocketHandle socketHandle);
        void OnChannelClosed(ble_l2cap_chan* nativeChan);

        int CloseChannel(Channel* channel);
        int CloseChannelNoLock(Channel* channel, bool force = false);
        int ReadChannel(Channel* channel, void* buffer, size_t len, size_t* receivedLen = nullptr, size_t timeout = SIZE_MAX);
        int WriteChannel(Channel* channel, const void* data, size_t len, size_t* sentLen = nullptr);

        int HandleL2CapEvent(ble_l2cap_event* event);
        static int L2CapEventCallback(ble_l2cap_event* event, void* arg);
    };

    class BleL2CapSocket : public Socket {
        friend class BleL2CapServer;
    private:
        BleL2CapServer* m_Server;
        std::shared_ptr<BleL2CapServer::Channel> m_Channel;

    private:
        BleL2CapSocket(BleL2CapServer* server, std::shared_ptr<BleL2CapServer::Channel> channel);
    public:
        virtual ~BleL2CapSocket();

        virtual int Write(const void* data, size_t len, size_t* sentLen = nullptr) override;
        virtual int Read(void* buffer, size_t len, size_t* receivedLen = nullptr, size_t timeout = SIZE_MAX) override;
    };
}