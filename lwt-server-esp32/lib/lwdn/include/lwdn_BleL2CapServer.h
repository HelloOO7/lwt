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
            uint16_t m_ConnHandle;
            ble_l2cap_chan* m_Chan{ nullptr };

            uint16_t m_Mtu;
            uint16_t m_ChunkSize;
            uint16_t m_ChunkSizeWithOverhead;
            uint16_t m_ChunkCountPerMbuf;
            uint16_t m_MbufSetCount;
            uint16_t m_ChunkCount;
            std::unique_ptr<os_membuf_t[]> m_MemBufData;
            os_mempool m_MemPool;
            os_mbuf_pool m_MbufPool;

            os_mbuf* m_CurRxBuf{ nullptr };
            size_t m_CurRxOffset{ 0 };
            size_t m_CurRxLen{ 0 };
            std::condition_variable m_RxBufAvailable;

            std::condition_variable m_TxUnstalled;

            Channel(SocketHandle socketHandle, uint16_t connHandle, uint16_t mtu, uint16_t chunkSize);
        };
    private:
        uint16_t m_Psm;
        uint16_t m_Mtu;

        std::mutex m_GlobalEventLock;
        std::vector<std::unique_ptr<Channel>> m_Channels;

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
        Channel* FindChannel(uint16_t connHandle, bool createIfNotFound = false);
        Channel* FindChannelBySocket(SocketHandle socketHandle);
        void OnChannelClosed(uint16_t connHandle);

        int CloseChannel(uint16_t connHandle);
        int ReadChannel(SocketHandle socketHandle, void* buffer, size_t len, size_t* receivedLen = nullptr, size_t timeout = SIZE_MAX);
        int WriteChannel(SocketHandle socketHandle, const void* data, size_t len, size_t* sentLen = nullptr);

        int HandleL2CapEvent(ble_l2cap_event* event);
        static int L2CapEventCallback(ble_l2cap_event* event, void* arg);
    };

    class BleL2CapSocket : public Socket {
        friend class BleL2CapServer;
    private:
        BleL2CapServer* m_Server;
        BleL2CapServer::SocketHandle m_SocketHandle;

    private:
        BleL2CapSocket(BleL2CapServer* server, BleL2CapServer::SocketHandle socketHandle);
    public:
        virtual ~BleL2CapSocket();

        virtual int Write(const void* data, size_t len, size_t* sentLen = nullptr) override;
        virtual int Read(void* buffer, size_t len, size_t* receivedLen = nullptr, size_t timeout = SIZE_MAX) override;
    };
}