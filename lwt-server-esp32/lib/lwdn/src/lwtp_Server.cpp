#include "lwtp_Server.h"
#include "lwtp_Protocol.h"
#include "esp_log.h"
#include <cstring>

namespace lwtp {

    static constexpr const char* TAG = "LWTP Server";

    void Server::AddSocket(lwdn::ServerSocket* socket, size_t taskCount, size_t taskStackSize)
    {
        std::lock_guard lock(m_Lock);
        for (size_t i = 0; i < taskCount; ++i) {
            auto task = std::make_unique<SocketTask>();
            task->m_Socket = socket;
            task->m_Server = this;
            xTaskCreate(SocketTaskFunc, "LWTP Socket task", taskStackSize, task.get(), tskIDLE_PRIORITY + 1, &task->m_TaskHandle);
            configASSERT(task->m_TaskHandle);
            m_SocketTasks.push_back(std::move(task));
        }
    }

    void Server::Serve(lwdn::Socket& socket)
    {
        int err;

        PacketHeader header;
        err = socket.ReadFully(&header, sizeof(header), m_RequestTimeout);
        if (err != 0) {
            ESP_LOGE(TAG, "Failed to read packet header: %d", err);
            return;
        }
        SwapByteOrder(header);

        if (memcmp(header.m_Magic, PacketHeader::MAGIC, sizeof(header.m_Magic)) != 0) {
            ESP_LOGE(TAG, "Invalid packet magic: %02x %02x %02x %02x", header.m_Magic[0], header.m_Magic[1], header.m_Magic[2], header.m_Magic[3]);
            return;
        }

        if (!header.m_Version) {
            ESP_LOGE(TAG, "Protocol version not set.");
            return;
        }
        ProtocolVersion ver = (ProtocolVersion)header.m_Version;
        if (ver >= ProtocolVersion::FIRST_NOT_SUPPORTED) {
            ESP_LOGE(TAG, "Unsupported protocol version: %d", header.m_Version);
            return;
        }

        uint16_t bufferSize = std::max<uint16_t>(header.m_HeaderSize, header.m_PayloadSize);

        PacketData payload(bufferSize);

        // exhaust header first
        if (header.m_HeaderSize > sizeof(header)) {
            err = socket.ReadFully(payload.data(), header.m_HeaderSize - sizeof(header), m_RequestTimeout);
            if (err != 0) {
                ESP_LOGE(TAG, "Failed to read packet header extension: %d", err);
                return;
            }
        }

        err = socket.ReadFully(payload.data(), header.m_PayloadSize, m_RequestTimeout);
        if (err != 0) {
            ESP_LOGE(TAG, "Failed to read packet payload: %d", err);
            return;
        }

        payload.resize(header.m_PayloadSize);

        auto response = ServeRequest(payload);

        PacketHeader responseHeader;
        memcpy(responseHeader.m_Magic, PacketHeader::MAGIC, sizeof(responseHeader.m_Magic));
        responseHeader.m_Version = header.m_Version;
        responseHeader.m_Flags = 0;
        responseHeader.m_HeaderSize = sizeof(responseHeader);
        responseHeader.m_PayloadSize = response.size();

        SwapByteOrder(responseHeader);

        err = socket.Write(&responseHeader, sizeof(responseHeader));
        if (err != 0) {
            ESP_LOGE(TAG, "Failed to write response header: %d", err);
            return;
        }
        err = socket.Write(response.data(), response.size());
        if (err != 0) {
            ESP_LOGE(TAG, "Failed to write response payload: %d", err);
            return;
        }
    }

    void Server::SocketTaskFunc(void* param) {
        SocketTask* task = static_cast<SocketTask*>(param);
        while (true) {
            auto socket = task->m_Socket->Accept();
            if (socket) {
                task->m_Server->Serve(*socket);
            }
            else {
                break;
            }
        }
    }
}