#pragma once

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "lwdn_ServerSocket.h"
#include <vector>
#include <mutex>
#include <memory>
#include <semaphore>
#include <atomic>
#include <variant>
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

    using ServerSocketHandle = size_t;
    static constexpr ServerSocketHandle INVALID_SERVER_SOCKET = 0;

    class SocketInterceptor;

    class Server {
    public:
        class SocketSession {
        public:
            using Tag = const int;
        private:
            struct TagEntry {
                Tag* m_Key;
                std::variant<std::monostate, psram_string, int, void*> m_Value;
            };

            std::unique_ptr<lwdn::Socket> m_Socket;
            std::vector<TagEntry> m_Tags;

        public:
            SocketSession(std::unique_ptr<lwdn::Socket> socket);

            lwdn::Socket& GetSocket();
            std::unique_ptr<lwdn::Socket> ExtractSocket();
            void ChangeSocket(std::unique_ptr<lwdn::Socket> newSocket);

            void SetTag(Tag* key, const psram_string& value);
            void SetTag(Tag* key, int value);
            void SetTag(Tag* key, void* value);
            void SetTag(Tag* key);
            bool GetTag(Tag* key, psram_string* outValue) const;
            bool GetTag(Tag* key, int* outValue) const;
            bool GetTag(Tag* key) const;
            bool GetTag(Tag* key, void** outValue) const;
            bool HasTag(Tag* key) const;
            void RemoveTag(Tag* key);

        private:
            TagEntry& FindOrAddTag(Tag* key);
            TagEntry* FindTag(Tag* key);
            const TagEntry* FindTag(Tag* key) const;

            template<typename T>
            bool GetTagImpl(Tag* key, T* outValue) const;
        };
    private:
        struct SocketTask {
            ServerSocketHandle m_SocketHandle;
            PSRAMTask m_Task;
            Server* m_Server;
            lwdn::ServerSocket* m_Socket;
        };

        std::mutex m_Lock;
        std::vector<std::unique_ptr<SocketTask>> m_SocketTasks;
        size_t m_RequestTimeout{ 5000 };
        std::vector<std::unique_ptr<SocketInterceptor>> m_Interceptors;

        std::atomic<bool> m_Closed{ false };
        std::counting_semaphore<8> m_TaskCloseSemaphore{ 0 };

        ServerSocketHandle m_NextSocketHandle{ 1 };

    public:
        Server() = default;
        virtual ~Server();

        ServerSocketHandle AddSocket(lwdn::ServerSocket* socket, size_t taskCount = 1, size_t taskStackSize = 4096);
        void AddInterceptor(std::unique_ptr<SocketInterceptor> interceptor, ServerSocketHandle socketFilter = INVALID_SERVER_SOCKET);

        /**
         * @brief Serve a protocol request. Child classes must implement this method.
         *
         * @param request
         * @return PacketData
         */
        virtual PacketData ServeRequest(SocketSession& session, const PacketData& request) = 0;
        /**
         * @brief Serve a raw low-level packet with a response. Child classes may opt to implement this
         * if they need to read additional data from the packet header, such as to respond to control messages,
         * or get additional information from the socket session.
         *
         * @param request
         * @return Packet
         */
        virtual Packet ServeRequest(SocketSession& session, const Packet& request);

        static PacketHeader CreateResponseHeader(const PacketHeader& requestHeader, const PacketData& response);
        static PacketData CreateControlCommandPayload(ControlCommand cmd);
        static Packet CreateControlCommandResponse(const PacketHeader& requestHeader, ControlCommand cmd);

    private:
        int OpenPacket(lwdn::Socket& socket, PacketHeader& header) const;
        int ReadPacket(lwdn::Socket& socket, const PacketHeader& header, PacketData& outPayload) const;
        int SendResponse(lwdn::Socket& socket, const PacketHeader& responseHeader, const PacketData& response) const;

        void Serve(ServerSocketHandle socketHandle, SocketSession& session);

        static void SocketTaskFunc(void* param);
    };

    class SocketInterceptor {
    public:
        class Chain {
            friend class Server;
        private:
            Server& m_Server;
            std::vector<std::unique_ptr<SocketInterceptor>>& m_Interceptors;
            ServerSocketHandle m_SocketHandle;
            size_t m_CurrentIndex{ 0 };

        private:
            Chain(Server& server, std::vector<std::unique_ptr<SocketInterceptor>>& interceptors, ServerSocketHandle socketHandle);
        public:
            ServerSocketHandle GetCurrentSocketHandle();
            Packet Traverse(Server::SocketSession& session, const Packet& request);
            int Traverse(Server::SocketSession& session, int error);
            void TraverseOpenSocket(Server::SocketSession& session);
            Packet Proceed(Server::SocketSession& session, const Packet& request);
            int Proceed(Server::SocketSession& session, int error);
            void ProceedOpenSocket(Server::SocketSession& session);
        };

    public:
        virtual ~SocketInterceptor() = default;

        virtual Packet Intercept(Server::SocketSession& session, const Packet& request, Chain& chain) = 0;
        virtual int InterceptError(Server::SocketSession& session, int error, Chain& chain);
        virtual void InterceptOpenSocket(Server::SocketSession& session, Chain& chain);
    };

    class ScopedSocketInterceptor : public SocketInterceptor {
    private:
        std::unique_ptr<SocketInterceptor> m_Base;
        ServerSocketHandle m_SocketFilter;
    public:
        ScopedSocketInterceptor(std::unique_ptr<SocketInterceptor> base, ServerSocketHandle socketFilter = INVALID_SERVER_SOCKET);

        bool IsSocketMatched(ServerSocketHandle socket) const;

        Packet Intercept(Server::SocketSession& session, const Packet& request, Chain& chain) override;
        int InterceptError(Server::SocketSession& session, int error, Chain& chain) override;
    };
}