#pragma once

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "lwdn_ServerSocket.h"
#include <vector>
#include <mutex>
#include <memory>
#include "PSRAMContainers.h"
#include "lwtp_Protocol.h"
#include "PSRAMTask.h"

namespace lwtp {

    using PacketData = psram_vector<uint8_t>;

    class Packet {
    private:
        PacketHeader m_Header;
        PacketData m_Payload;
    public:
        Packet(const PacketHeader& header, PacketData&& payload);
        Packet(const PacketHeader&& header, PacketData&& payload);

        const PacketHeader& GetHeader() const;
        const PacketData& GetPayload() const;

        bool IsControlMessage() const;
        ControlCommand ParseControlCommand() const;
    };

    class SocketInterceptor;

    class Server {
    public:
        class SocketSession {
        public:
            using Flag = uint8_t;
        private:
            std::unique_ptr<lwdn::Socket> m_Socket;
            size_t m_Flags{ 0 };
        public:
            SocketSession(std::unique_ptr<lwdn::Socket> socket);

            lwdn::Socket& GetSocket();
            std::unique_ptr<lwdn::Socket> ExtractSocket();
            void ChangeSocket(std::unique_ptr<lwdn::Socket> newSocket);

            void SetFlag(Flag base, Flag index);
            void ClearFlag(Flag base, Flag index);
            bool IsFlagSet(Flag base, Flag index) const;
        };
    private:
        struct SocketTask {
            PSRAMTask m_Task;
            Server* m_Server;
            lwdn::ServerSocket* m_Socket;
        };

        std::mutex m_Lock;
        std::vector<std::unique_ptr<SocketTask>> m_SocketTasks;
        size_t m_RequestTimeout{ 5000 };
        std::vector<std::unique_ptr<SocketInterceptor>> m_Interceptors;

    public:
        Server() = default;
        virtual ~Server();

        void AddSocket(lwdn::ServerSocket* socket, size_t taskCount = 1, size_t taskStackSize = 4096);
        void AddInterceptor(std::unique_ptr<SocketInterceptor> interceptor);

        /**
         * @brief Serve a protocol request. Child classes must implement this method.
         *
         * @param request
         * @return PacketData
         */
        virtual PacketData ServeRequest(const PacketData& request) = 0;
        /**
         * @brief Serve a raw low-level packet with a response. Child classes may opt to implement this
         * if they need to read additional data from the packet header, such as to respond to control messages.
         *
         * @param request
         * @return Packet
         */
        virtual Packet ServeRequest(const Packet& request);

        static PacketHeader CreateResponseHeader(const PacketHeader& requestHeader, const PacketData& response);
        static PacketData CreateControlCommandPayload(ControlCommand cmd);
        static Packet CreateControlCommandResponse(const PacketHeader& requestHeader, ControlCommand cmd);

    private:
        int OpenPacket(lwdn::Socket& socket, PacketHeader& header) const;
        int ReadPacket(lwdn::Socket& socket, const PacketHeader& header, PacketData& outPayload) const;
        int SendResponse(lwdn::Socket& socket, const PacketHeader& responseHeader, const PacketData& response) const;

        void Serve(SocketSession& session);

        static void SocketTaskFunc(void* param);
    };

    class SocketInterceptor {
    public:
        class Chain {
            friend class Server;
        private:
            Server& m_Server;
            std::vector<std::unique_ptr<SocketInterceptor>>& m_Interceptors;
            std::vector<Server::SocketSession::Flag> m_SessionFlagOffsets;
            size_t m_CurrentIndex{ 0 };

        private:
            Chain(Server& server, std::vector<std::unique_ptr<SocketInterceptor>>& interceptors);
        public:
            Packet Traverse(Server::SocketSession& session, const Packet& request);
            int Traverse(Server::SocketSession& session, int error);
            Packet Proceed(Server::SocketSession& session, const Packet& request);
            int Proceed(Server::SocketSession& session, int error);
        };

    public:
        virtual ~SocketInterceptor() = default;

        virtual Server::SocketSession::Flag GetUsedFlagCount() const;

        virtual int InterceptError(Server::SocketSession& session, int error, Chain& chain, Server::SocketSession::Flag sessionFlagBase);
        virtual Packet Intercept(Server::SocketSession& session, const Packet& request, Chain& chain, Server::SocketSession::Flag sessionFlagBase) = 0;
    };
}