#include "lwtp_Server.h"
#include "lwtp_Protocol.h"
#include "esp_log.h"
#include <cstring>
#include <utility>

namespace lwtp {

    static constexpr const char* TAG = "LWTP Server";

    Packet::Packet(const PacketHeader& header, PacketData&& payload) :
        m_Header(header),
        m_Payload(std::move(payload))
    {
    }

    Packet::Packet(const PacketHeader&& header, PacketData&& payload) :
        m_Header(std::move(header)),
        m_Payload(std::move(payload))
    {
    }

    const PacketHeader& Packet::GetHeader() const
    {
        return m_Header;
    }

    const PacketData& Packet::GetPayload() const
    {
        return m_Payload;
    }

    bool Packet::IsControlMessage() const
    {
        return m_Header.m_Flags & PacketHeader::FLAG_CONTROL_MESSAGE;
    }

    ControlCommand Packet::ParseControlCommand() const
    {
        if (!IsControlMessage()) {
            return ControlCommand::INVALID;
        }

        std::underlying_type_t<ControlCommand> cmd = 0;
        for (uint8_t byte : m_Payload) {
            cmd <<= 8;
            cmd |= byte;
        }

        return static_cast<ControlCommand>(cmd);
    }

    Server::SocketSession::SocketSession(std::unique_ptr<lwdn::Socket> socket)
        : m_Socket(std::move(socket))
    {
    }

    lwdn::Socket& Server::SocketSession::GetSocket()
    {
        return *m_Socket;
    }

    std::unique_ptr<lwdn::Socket> Server::SocketSession::ExtractSocket()
    {
        return std::move(m_Socket);
    }

    void Server::SocketSession::ChangeSocket(std::unique_ptr<lwdn::Socket> newSocket)
    {
        m_Socket = std::move(newSocket);
    }

    void Server::SocketSession::SetFlag(Flag base, Flag index)
    {
        index += base;
        m_Flags |= (1ULL << index);
    }

    void Server::SocketSession::ClearFlag(Flag base, Flag index)
    {
        index += base;
        m_Flags &= ~(1ULL << index);
    }

    bool Server::SocketSession::IsFlagSet(Flag base, Flag index) const
    {
        index += base;
        return m_Flags & (1ULL << index);
    }

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

    void Server::AddInterceptor(std::unique_ptr<SocketInterceptor> interceptor)
    {
        std::lock_guard lock(m_Lock);
        m_Interceptors.push_back(std::move(interceptor));
    }

    void Server::Serve(SocketSession& session)
    {
        SocketInterceptor::Chain interceptors(*this, m_Interceptors);

        int numServed = 0;

        do {
            // set current socket at start of one request/response transaction.
            // this is so that replies are delivered over the same transport as requests even if the actual socket
            // changes during the transaction (e.g. due to START_TLS).
            // the pointer to the currentSocket must remain valid - the interceptor is not allowed to delete it.
            // this is usually satisfied by the fact that the interceptor wraps the socket into another, where it
            // is referenced again by an unique_ptr.
            lwdn::Socket& currentSocket = session.GetSocket();
            int err;

            PacketHeader header;
            err = OpenPacket(currentSocket, header);
            if (err != 0) {
                err = interceptors.Traverse(session, err);
                if (err == EAGAIN) {
                    ESP_LOGI(TAG, "Error was intercepted, retry OpenPacket");
                    // interceptor handled the error, so try again.
                    // we MUST retry the whole loop again so that currentSocket is updated
                    // in case that the interceptor changed it
                    continue;
                }
            }
            if (err == ECONNRESET && numServed > 0) {
                ESP_LOGI(TAG, "Client disconnected after having served %d request(s)", numServed);
            }
            if (err != 0) {
                return;
            }

            PacketData payload;

            err = ReadPacket(currentSocket, header, payload);
            if (err != 0) {
                return;
            }

            Packet packet(std::move(header), std::move(payload));

            auto response = interceptors.Traverse(session, packet);

            err = SendResponse(currentSocket, response.GetHeader(), response.GetPayload());
            if (err != 0) {
                return;
            }

            numServed++;
        } while (true);
    }

    Packet Server::ServeRequest(const Packet& request)
    {
        std::lock_guard lock(m_Lock);

        if (request.IsControlMessage()) {
            // no CCs implemented in base server implementation
            return CreateControlCommandResponse(request.GetHeader(), ControlCommand::COMMAND_NOT_IMPLEMENTED);
        }

        auto served = ServeRequest(request.GetPayload());
        return Packet(Server::CreateResponseHeader(request.GetHeader(), served), std::move(served));
    }

    int Server::OpenPacket(lwdn::Socket& socket, PacketHeader& header) const {
        int err;

        err = socket.ReadFully(&header, sizeof(header), m_RequestTimeout);
        if (err == ECONNRESET) {
            ESP_LOGE(TAG, "OpenPacket: client disconnected");
            return err;
        }
        if (err != 0) {
            ESP_LOGE(TAG, "Failed to read packet header: %d", err);
            return err;
        }
        SwapByteOrder(header);

        if (memcmp(header.m_Magic, PacketHeader::MAGIC, sizeof(header.m_Magic)) != 0) {
            ESP_LOGE(TAG, "Invalid packet magic: %02x %02x %02x %02x", header.m_Magic[0], header.m_Magic[1], header.m_Magic[2], header.m_Magic[3]);
            return EPROTO;
        }

        if (!header.m_Version) {
            ESP_LOGE(TAG, "Protocol version not set.");
            return EPROTO;
        }

        return 0;
    }

    int Server::ReadPacket(lwdn::Socket& socket, const PacketHeader& header, PacketData& outPayload) const {
        int err;

        uint16_t bufferSize = std::max<uint16_t>(header.m_HeaderSize, header.m_PayloadSize);
        outPayload.resize(bufferSize);

        // exhaust header first
        if (header.m_HeaderSize > sizeof(header)) {
            err = socket.ReadFully(outPayload.data(), header.m_HeaderSize - sizeof(header), m_RequestTimeout);
            if (err != 0) {
                ESP_LOGE(TAG, "Failed to read packet header extension: %d", err);
                return err;
            }
        }

        err = socket.ReadFully(outPayload.data(), header.m_PayloadSize, m_RequestTimeout);
        if (err != 0) {
            ESP_LOGE(TAG, "Failed to read packet payload: %d", err);
            return err;
        }

        outPayload.resize(header.m_PayloadSize);
        return 0;
    }

    PacketHeader Server::CreateResponseHeader(const PacketHeader& requestHeader, const PacketData& response) {
        PacketHeader responseHeader;
        memcpy(responseHeader.m_Magic, PacketHeader::MAGIC, sizeof(responseHeader.m_Magic));
        responseHeader.m_Version = requestHeader.m_Version;
        responseHeader.m_Flags = 0;
        responseHeader.m_HeaderSize = sizeof(responseHeader);
        responseHeader.m_PayloadSize = response.size();
        return responseHeader;
    }

    PacketData Server::CreateControlCommandPayload(ControlCommand cmd) {
        PacketData payload;
        auto cmdValue = std::to_underlying(cmd);
        for (size_t i = 0; i < sizeof(cmdValue); ++i) {
            // push big-endian byte order
            auto byte = (cmdValue >> (8 * (sizeof(cmdValue) - 1 - i))) & 0xFF;
            if (byte != 0 || !payload.empty()) {
                payload.push_back(byte);
            }
        }
        return payload;
    }

    Packet Server::CreateControlCommandResponse(const PacketHeader& requestHeader, ControlCommand cmd) {
        auto payload = CreateControlCommandPayload(cmd);
        PacketHeader header = CreateResponseHeader(requestHeader, payload);
        header.m_Flags |= PacketHeader::FLAG_CONTROL_MESSAGE;
        return Packet(std::move(header), std::move(payload));
    }

    int Server::SendResponse(lwdn::Socket& socket, const PacketHeader& responseHeader, const PacketData& response) const {
        PacketHeader headerToSend = responseHeader;
        SwapByteOrder(headerToSend);

        int err = socket.Write(&headerToSend, sizeof(headerToSend));
        if (err != 0) {
            ESP_LOGE(TAG, "Failed to write response header: %d", err);
            return err;
        }
        err = socket.Write(response.data(), response.size());
        if (err != 0) {
            ESP_LOGE(TAG, "Failed to write response payload: %d", err);
            return err;
        }
        return 0;
    }

    void Server::SocketTaskFunc(void* param) {
        SocketTask* task = static_cast<SocketTask*>(param);
        while (true) {
            auto socket = task->m_Socket->Accept();
            if (socket) {
                SocketSession session(std::move(socket));
                task->m_Server->Serve(session);
            }
            else {
                break;
            }
        }
    }

    SocketInterceptor::Chain::Chain(Server& server, std::vector<std::unique_ptr<SocketInterceptor>>& interceptors) :
        m_Server{ server },
        m_Interceptors{ interceptors },
        m_SessionFlagOffsets(interceptors.size(), 0)
    {
        Server::SocketSession::Flag currentFlagOffset = 0;
        for (size_t i = 0; i < interceptors.size(); ++i) {
            m_SessionFlagOffsets[i] = currentFlagOffset;
            currentFlagOffset += interceptors[i]->GetUsedFlagCount();
        }
    }

    Packet SocketInterceptor::Chain::Traverse(Server::SocketSession& session, const Packet& request)
    {
        m_CurrentIndex = 0;
        return Proceed(session, request);
    }

    int SocketInterceptor::Chain::Traverse(Server::SocketSession& session, int error)
    {
        m_CurrentIndex = 0;
        return Proceed(session, error);
    }

    Packet SocketInterceptor::Chain::Proceed(Server::SocketSession& session, const Packet& request)
    {
        if (m_CurrentIndex < m_Interceptors.size()) {
            size_t index = m_CurrentIndex++;
            auto& interceptor = m_Interceptors[index];
            return interceptor->Intercept(session, request, *this, m_SessionFlagOffsets[index]);
        }
        else {
            auto served = m_Server.ServeRequest(request.GetPayload());
            return Packet(Server::CreateResponseHeader(request.GetHeader(), served), std::move(served));
        }
    }

    int SocketInterceptor::Chain::Proceed(Server::SocketSession& session, int error)
    {
        if (m_CurrentIndex < m_Interceptors.size()) {
            size_t index = m_CurrentIndex++;
            auto& interceptor = m_Interceptors[index];
            return interceptor->InterceptError(session, error, *this, m_SessionFlagOffsets[index]);
        }
        else {
            return error;
        }
    }

    Server::SocketSession::Flag SocketInterceptor::GetUsedFlagCount() const
    {
        return 0;
    }

    int SocketInterceptor::InterceptError(Server::SocketSession& session, int error, Chain& chain, Server::SocketSession::Flag sessionFlagBase)
    {
        return error;
    }
}