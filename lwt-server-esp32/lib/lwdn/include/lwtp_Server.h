#pragma once

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "lwdn_ServerSocket.h"
#include <vector>
#include <mutex>
#include <memory>
#include "PSRAMContainers.h"

namespace lwtp {

    using PacketData = psram_vector<uint8_t>;

    class Server {
    private:
        struct SocketTask {
            TaskHandle_t m_TaskHandle{ nullptr };
            Server* m_Server;
            lwdn::ServerSocket* m_Socket;
        };

        std::mutex m_Lock;
        std::vector<std::unique_ptr<SocketTask>> m_SocketTasks;
        size_t m_RequestTimeout{ 5000 };

    public:
        void AddSocket(lwdn::ServerSocket* socket, size_t taskCount = 1, size_t taskStackSize = 4096);

        virtual PacketData ServeRequest(const PacketData& request) = 0;

    private:
        void Serve(lwdn::Socket& socket);

        static void SocketTaskFunc(void* param);
    };
}