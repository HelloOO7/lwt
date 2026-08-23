#pragma once

#include <string>
#include <stdint.h>
#include <vector>
#include <utility>
#include <optional>
#include <algorithm>
#include <thread>
#include <functional>
#include "EventQueue.h"
#include "mdns.h"

namespace vdv301
{

    /**
     * @brief mDNS-based service discovery as per the VDV 301 standard.
     */
    class ServiceDiscovery
    {
    public:
        using TxtRecord = std::pair<std::string, std::string>;
        using TxtRecordSet = std::vector<TxtRecord>;

        enum class Protocol {
            TCP,
            UDP
        };

        class QueryBuilder;

        class Query {
            friend class QueryBuilder;
            friend class ServiceDiscovery;
        private:
            std::optional<std::string> m_InstanceName;
            TxtRecordSet m_TxtRecords;
            bool m_RequireIP{ true };
        };

        class QueryBuilder {
        private:
            Query m_Query;
        public:
            QueryBuilder();
            QueryBuilder& FilterInstanceName(const std::string& instanceName);
            QueryBuilder& FilterTxtRecord(const std::string& key, const std::string& value);
            QueryBuilder& SetRequireIP(bool requireIP);
            Query Build() const;
        };

        class Result
        {
            friend class ServiceDiscovery;

        private:
            std::string m_InstanceName;
            std::string m_HostName;
            uint16_t m_Port;
            TxtRecordSet m_TxtRecords;
            esp_netif_t* m_Interface;
            std::vector<esp_ip_addr_t> m_IPAddresses;

        public: // for emplace
            Result(const mdns_result_t* result);

        public:
            const std::string& GetInstanceName() const;
            const std::string& GetHostName() const;
            uint16_t GetPort() const;
            const std::optional<std::string> GetTxtRecord(const std::string& key) const;
            esp_netif_t* GetInterface() const;
            const esp_ip_addr_t* GetIPAddress(int type) const;
            const esp_ip4_addr_t* GetIPv4Address() const;

            bool operator==(const Result& other) const;
            bool operator!=(const Result& other) const;

        private:
            void MergeTxtRecords(const mdns_result_t* result);
            void UpdateAddresses(const mdns_result_t* result);
        };

        class ResultSetAccessor {
            friend class ServiceDiscovery;
        private:
            const std::vector<Result>& m_Results;

        private:
            ResultSetAccessor(const std::vector<Result>& results);
        public:
            bool HasResult() const;
            const std::vector<Result>& GetAllResults() const;
            const Result* GetAnyResult() const;
            template<class Compare>
            const Result* GetBestResult(Compare compare) const {
                auto find = std::max_element(m_Results.begin(), m_Results.end(), compare);
                return find != m_Results.end() ? &(*find) : nullptr;
            }
        };

        using BrowseCallback = std::function<void(const ResultSetAccessor& results)>;
        using BrowseHandle = size_t;

    private:
        struct QueryBrowseState {
            BrowseHandle m_Handle;
            Query m_Query;
            std::vector<Result> m_LastResults;
            BrowseCallback m_Callback;
        };

        struct AdditionalQueryState {
            mdns_search_once_t* m_SearchHandle;
            std::string m_InstanceName;
            int m_Type;
        };

    private:
        EventQueue m_EventQueue;

        std::string m_ServiceType;
        Protocol m_Protocol;
        std::string m_ProtocolStr;
        std::string m_Address;
        bool m_TrustNANPeers{ false };

        std::mutex m_BrowseStateMutex;
        size_t m_NextBrowseHandle{ 1 };
        std::vector<QueryBrowseState> m_ActiveBrowses;
        mdns_browse_t* m_SdkBrowseHandle{ nullptr };

        std::mutex m_DNSCacheMutex;
        std::vector<Result> m_DNSCache;

        std::mutex m_AdditionalQueryMutex;
        std::vector<AdditionalQueryState> m_ActiveAdditionalQueries;

        esp_event_handler_instance_t m_EthIPEventInstance;
        esp_event_handler_instance_t m_StaIPEventInstance;
        esp_event_handler_instance_t m_IPv6EventInstance;

    public:
        /**
         * @brief Create a new service discovery provider. There must always be at most one instance of
         * ServiceDiscovery per service type + protocol, otherwise the behavior is undefined.
         * You must call mdns_init() before creating instances of this class, otherwise an error will occur.
         *
         * @param serviceType service type without leading underscore and without trailing "._tcp" or "._udp", e.g. "my-service"
         * @param protocol protocol which the service is using
         * @param taskPriority priority for the internal event queue task
         */
        ServiceDiscovery(const std::string& serviceType, Protocol protocol, int taskPriority = EventQueue::DEFAULT_TASK_PRIORITY);
        ~ServiceDiscovery();

        /**
         * @brief Enable trusting NAN (Wi-Fi Aware) peers during service discovery.
         * This is disabled by default, as it would allow NAN devices connected over a datapath
         * to inject themselves into service discovery, which may not be desirable.
         * 
         * @param trust 
         */
        void SetTrustNANPeers(bool trust);

        BrowseHandle StartBrowse(const Query& query, BrowseCallback callback);
        void StopBrowse(BrowseHandle handle);
        void RestartAllBrowses();

        static const char* ProtocolToAddressString(Protocol protocol);
        static std::string BuildMdnsAddress(const std::string& serviceType, Protocol protocol);
        static std::string BuildMdnsAddress(const std::string& serviceType, const std::string& protocolStr);

    private:
        void SdkStartBrowse();
        void SdkStopBrowse();

        static bool MatchQueryResult(const Query& query, const Result& result);
        bool UpdateBrowseFromCache(QueryBrowseState& browse);
        void HandleBrowseResult(mdns_result_t* result, bool fetchAdditional = false);
        bool SendSyncQuery(const char* instanceName, uint16_t type, uint32_t timeout, size_t maxResults);
        bool BeginAsyncQuery(const char* instanceName, uint16_t type, uint32_t timeout, size_t maxResults);
        bool IsAsyncQueryActive(const char* instanceName, uint16_t type) const;
        void HandleAsyncQueryResults(mdns_search_once_t* searchHandle);
        static void GlobalBrowseNotifyCallback(mdns_result_t* results);
        static void GlobalAsyncResultNotifyCallback(mdns_search_once_t* search);
        const Result* FindAnyResultByHostName(const std::string& hostName);
        std::string FindInstanceNameForSearchHandle(mdns_search_once_t* searchHandle);
        void OnIPAddressAssigned(ip_event_got_ip_t* data);
        void OnIPAddressAssigned(ip_event_got_ip6_t* data);
        static bool DeviceHasAnyIPAddress();
        static void EventGotIPCallback(void* arg, esp_event_base_t event_base, int32_t event_id, void* event_data);

        bool IsInterfaceTrusted(esp_netif_t* netif) const;

        void UpdateBrowseResultsAsync();
    };

    ServiceDiscovery HttpServiceDiscovery(int taskPriority = EventQueue::DEFAULT_TASK_PRIORITY);
}