#include "vdv_ServiceDiscovery.h"
#include "mdns.h"
#include <mutex>
#include <unordered_map>
#include "esp_log.h"
#include <cstring>

namespace vdv301
{
    static constexpr const char* TAG = "ServiceDiscovery";

    static constexpr EventQueue::EventTag EVENT_TAG_UPDATE_BROWSE_RESULTS = 1;

    ServiceDiscovery::QueryBuilder::QueryBuilder()
    {

    }

    ServiceDiscovery::QueryBuilder& ServiceDiscovery::QueryBuilder::FilterInstanceName(const std::string& instanceName)
    {
        m_Query.m_InstanceName = instanceName;
        return *this;
    }

    ServiceDiscovery::QueryBuilder& ServiceDiscovery::QueryBuilder::FilterTxtRecord(const std::string& key, const std::string& value)
    {
        m_Query.m_TxtRecords.emplace_back(key, value);
        return *this;
    }

    ServiceDiscovery::QueryBuilder& ServiceDiscovery::QueryBuilder::SetRequireIP(bool requireIP)
    {
        m_Query.m_RequireIP = requireIP;
        return *this;
    }

    ServiceDiscovery::Query ServiceDiscovery::QueryBuilder::Build() const
    {
        return m_Query;
    }

    ServiceDiscovery::Result::Result(const mdns_result_t* result) :
        m_InstanceName(result->instance_name),
        m_HostName(result->hostname),
        m_Port(result->port),
        m_Interface(result->esp_netif)
    {
        for (int i = 0; i < result->txt_count; i++)
        {
            m_TxtRecords.emplace_back(result->txt[i].key, result->txt[i].value);
        }
        UpdateAddresses(result);
    }

    void ServiceDiscovery::Result::MergeTxtRecords(const mdns_result_t* result)
    {
        for (int i = 0; i < result->txt_count; i++)
        {
            const char* key = result->txt[i].key;
            const char* value = result->txt[i].value;

            auto find = std::find_if(
                m_TxtRecords.begin(),
                m_TxtRecords.end(),
                [key](const TxtRecord& record) {
                    return record.first == key;
                }
            );
            if (find != m_TxtRecords.end()) {
                find->second = value;
            }
            else {
                m_TxtRecords.emplace_back(key, value);
            }
        }
    }

    void ServiceDiscovery::Result::UpdateAddresses(const mdns_result_t* result)
    {
        std::erase_if(
            m_IPAddresses,
            [&result](const esp_ip_addr_t& addr) {
                // remove addresses of types which are in the new data
                auto* addrList = result->addr;
                while (addrList) {
                    if (addr.type == addrList->addr.type) {
                        return true;
                    }
                    addrList = addrList->next;
                }
                return false;
            }
        );

        auto* addr = result->addr;
        while (addr) {
            m_IPAddresses.push_back(addr->addr);
            addr = addr->next;
        }
    }

    const std::string& ServiceDiscovery::Result::GetInstanceName() const
    {
        return m_InstanceName;
    }

    const std::string& ServiceDiscovery::Result::GetHostName() const
    {
        return m_HostName;
    }

    uint16_t ServiceDiscovery::Result::GetPort() const
    {
        return m_Port;
    }

    esp_netif_t* ServiceDiscovery::Result::GetInterface() const
    {
        return m_Interface;
    }

    const std::optional<std::string> ServiceDiscovery::Result::GetTxtRecord(const std::string& key) const
    {
        for (auto&& record : m_TxtRecords)
        {
            if (record.first == key)
            {
                return record.second;
            }
        }
        return std::nullopt;
    }

    const esp_ip_addr_t* ServiceDiscovery::Result::GetIPAddress(int type) const
    {
        for (auto& addr : m_IPAddresses) {
            if (addr.type == type) {
                return &addr;
            }
        }
        return nullptr;
    }

    const esp_ip4_addr_t* ServiceDiscovery::Result::GetIPv4Address() const
    {
        const esp_ip_addr_t* addr = GetIPAddress(ESP_IPADDR_TYPE_V4);
        return addr != nullptr ? &addr->u_addr.ip4 : nullptr;
    }

    bool ServiceDiscovery::Result::operator==(const Result& other) const
    {
        if (this == &other) {
            return true;
        }
        if (m_InstanceName != other.m_InstanceName) {
            ESP_LOGI(TAG, "Result does not match: instance name differs (%s vs %s)", m_InstanceName.c_str(), other.m_InstanceName.c_str());
            return false;
        }
        if (m_HostName != other.m_HostName) {
            ESP_LOGI(TAG, "Result does not match: host name differs (%s vs %s)", m_HostName.c_str(), other.m_HostName.c_str());
            return false;
        }
        if (m_Port != other.m_Port) {
            ESP_LOGI(TAG, "Result does not match: port differs (%u vs %u)", m_Port, other.m_Port);
            return false;
        }
        if (m_Interface != other.m_Interface) {
            ESP_LOGI(TAG, "Result does not match: interface differs (%p vs %p)", m_Interface, other.m_Interface);
            return false;
        }
        if (m_TxtRecords.size() != other.m_TxtRecords.size()) {
            ESP_LOGI(TAG, "Result does not match: number of TXT records differs (%zu vs %zu)", m_TxtRecords.size(), other.m_TxtRecords.size());
            return false;
        }
        if (m_IPAddresses.size() != other.m_IPAddresses.size()) {
            ESP_LOGI(TAG, "Result does not match: number of IP addresses differs (%zu vs %zu)", m_IPAddresses.size(), other.m_IPAddresses.size());
            return false;
        }
        if (m_InstanceName != other.m_InstanceName ||
            m_HostName != other.m_HostName ||
            m_Port != other.m_Port ||
            m_Interface != other.m_Interface ||
            m_TxtRecords.size() != other.m_TxtRecords.size() ||
            m_IPAddresses.size() != other.m_IPAddresses.size())
        {
            ESP_LOGI(TAG, "Result does not match: instance name, host name, port, interface, number of TXT records, or number of IP addresses differ");
            return false;
        }
        for (auto&& txtRecord : m_TxtRecords) {
            if (std::find(other.m_TxtRecords.begin(), other.m_TxtRecords.end(), txtRecord) == other.m_TxtRecords.end()) {
                ESP_LOGI(TAG, "TXT record %s=%s does not match any record in other result", txtRecord.first.c_str(), txtRecord.second.c_str());
                return false;
            }
        }
        for (auto&& addr : m_IPAddresses) {
            if (std::none_of(
                other.m_IPAddresses.begin(),
                other.m_IPAddresses.end(),
                [&addr](const esp_ip_addr_t& otherAddr) {
                    return addr.type == otherAddr.type && memcmp(&addr.u_addr, &otherAddr.u_addr, sizeof(addr.u_addr)) == 0;
                }
            )) {
                ESP_LOGI(TAG, "IP address does not match any address in other result");
                return false;
            }
        }
        return true;
    }

    bool ServiceDiscovery::Result::operator!=(const Result& other) const
    {
        return !(*this == other);
    }

    ServiceDiscovery::ResultSetAccessor::ResultSetAccessor(const std::vector<Result>& results) :
        m_Results{ results }
    {

    }

    bool ServiceDiscovery::ResultSetAccessor::HasResult() const
    {
        return !m_Results.empty();
    }

    const std::vector<ServiceDiscovery::Result>& ServiceDiscovery::ResultSetAccessor::GetAllResults() const
    {
        return m_Results;
    }

    const ServiceDiscovery::Result* ServiceDiscovery::ResultSetAccessor::GetAnyResult() const
    {
        return m_Results.empty() ? nullptr : &m_Results.front();
    }

    static int g_InstanceCount = 0;
    static std::mutex g_GlobalStateMutex;
    static std::unordered_map<std::string, ServiceDiscovery*> g_AddressToInstanceMap;

    static std::mutex g_GlobalAsyncQueryMutex;
    static std::unordered_map<mdns_search_once_t*, ServiceDiscovery*> g_AsyncQueryToInstanceMap;

    ServiceDiscovery::ServiceDiscovery(const std::string& serviceType, Protocol protocol) :
        m_EventQueue{ "ServiceDiscovery", 10 },
        m_ServiceType{ "_" + serviceType },
        m_Protocol{ protocol },
        m_ProtocolStr{ ProtocolToAddressString(protocol) },
        m_Address{ BuildMdnsAddress(m_ServiceType, m_ProtocolStr) }
    {
        std::lock_guard lock(g_GlobalStateMutex);
        if (g_InstanceCount++ == 0) {
            ESP_ERROR_CHECK(mdns_init());
        }
        g_AddressToInstanceMap[m_Address] = this;
    }

    ServiceDiscovery::~ServiceDiscovery()
    {
        {
            std::scoped_lock lock(m_AdditionalQueryMutex, g_GlobalAsyncQueryMutex);
            for (auto&& query : m_ActiveAdditionalQueries) {
                g_AsyncQueryToInstanceMap.erase(query.m_SearchHandle);
            }
        }

        m_EventQueue.Close();

        {
            std::lock_guard lock(g_GlobalStateMutex);
            g_AddressToInstanceMap.erase(m_Address);
            if (--g_InstanceCount == 0) {
                mdns_free();
            }
        }
    }

    void ServiceDiscovery::GlobalBrowseNotifyCallback(mdns_result_t* result) {
        // notification is sent for each result (though we *could* traverse the whole linked list if we wanted to...)
        std::string address = BuildMdnsAddress(result->service_type, result->proto);
        std::lock_guard lock(g_GlobalStateMutex);
        auto find = g_AddressToInstanceMap.find(address);
        if (find != g_AddressToInstanceMap.end()) {
            ServiceDiscovery* instance = find->second;
            instance->HandleBrowseResult(result, true);
        }
    }

    void ServiceDiscovery::GlobalAsyncResultNotifyCallback(mdns_search_once_t* search) {
        std::lock_guard lock(g_GlobalAsyncQueryMutex);
        auto find = g_AsyncQueryToInstanceMap.find(search);
        if (find != g_AsyncQueryToInstanceMap.end()) {
            ServiceDiscovery* instance = find->second;
            instance->HandleAsyncQueryResults(search);
        }
        else {
            ESP_LOGW(TAG, "Orphaned async query result received, deleting search handle");
            mdns_query_async_delete(search);
        }
    }

    ServiceDiscovery::BrowseHandle ServiceDiscovery::StartBrowse(const Query& query, BrowseCallback callback)
    {
        std::lock_guard lock(m_BrowseStateMutex);
        BrowseHandle handle = m_NextBrowseHandle++;
        m_ActiveBrowses.push_back({ handle, query, {}, callback });
        if (m_SdkBrowseHandle == nullptr) {
            ESP_LOGI(TAG, "Starting mDNS browse for service type %s and protocol %s", m_ServiceType.c_str(), m_ProtocolStr.c_str());
            m_SdkBrowseHandle = mdns_browse_new(m_ServiceType.c_str(), m_ProtocolStr.c_str(), ServiceDiscovery::GlobalBrowseNotifyCallback);
            ESP_ERROR_CHECK(m_SdkBrowseHandle != nullptr ? ESP_OK : ESP_FAIL);
        }
        return handle;
    }

    void ServiceDiscovery::StopBrowse(BrowseHandle handle)
    {
        std::lock_guard lock(m_BrowseStateMutex);
        auto find = std::find_if(
            m_ActiveBrowses.begin(),
            m_ActiveBrowses.end(),
            [handle](const QueryBrowseState& state) {
                return state.m_Handle == handle;
            }
        );
        if (find != m_ActiveBrowses.end()) {
            m_ActiveBrowses.erase(find);

            if (m_ActiveBrowses.empty() && m_SdkBrowseHandle != nullptr) {
                ESP_LOGI(TAG, "Stopping mDNS browse for service type %s and protocol %s", m_ServiceType.c_str(), m_ProtocolStr.c_str());
                mdns_browse_delete(m_ServiceType.c_str(), m_ProtocolStr.c_str());
                m_SdkBrowseHandle = nullptr;
            }
        }
    }

    void ServiceDiscovery::HandleBrowseResult(mdns_result_t* result, bool fetchAdditional) {
        ESP_LOGI(TAG, "Received mDNS browse result for service instance %s at host %s:%u TTL: %u", result->instance_name, result->hostname, result->port, result->ttl);
        auto* addr = result->addr;
        while (addr) {
            if (addr->addr.type == ESP_IPADDR_TYPE_V4) {
                ESP_LOGI(TAG, " - IP: " IPSTR, IP2STR(&addr->addr.u_addr.ip4));
            }
            else if (addr->addr.type == ESP_IPADDR_TYPE_V6) {
                ESP_LOGI(TAG, " - IP: " IPV6STR, IPV62STR(addr->addr.u_addr.ip6));
            }
            addr = addr->next;
        }
        for (int i = 0; i < result->txt_count; i++)
        {
            ESP_LOGI(TAG, " - TXT: %s=%s", result->txt[i].key, result->txt[i].value);
        }

        {
            std::lock_guard lock(m_DNSCacheMutex);

            if (result->ttl == 0) {
                std::erase_if(
                    m_DNSCache,
                    [&result](const Result& entry) {
                        return entry.m_InstanceName == result->instance_name;
                    }
                );
            }
            else {
                bool instanceMatched = false;
                bool hasTxt = result->txt_count > 0;
                bool hasIp = result->addr != nullptr;
                for (auto&& cacheEntry : m_DNSCache) {
                    if (result->instance_name && cacheEntry.m_InstanceName == result->instance_name) {
                        instanceMatched = true;
                        cacheEntry.m_HostName = result->hostname;
                        cacheEntry.m_Port = result->port;
                        cacheEntry.m_Interface = result->esp_netif;
                        if (result->txt_count) {
                            cacheEntry.MergeTxtRecords(result);
                        }
                        hasTxt |= cacheEntry.m_TxtRecords.size() > 0;
                        hasIp |= !cacheEntry.m_IPAddresses.empty();
                    }
                    if (cacheEntry.m_HostName == result->hostname && result->addr) {
                        cacheEntry.UpdateAddresses(result);
                    }
                }
                if (!instanceMatched && result->instance_name) {
                    m_DNSCache.emplace_back(result);
                    if (!result->addr) {
                        auto* existingSameHost = FindAnyResultByHostName(result->hostname);
                        if (existingSameHost) {
                            m_DNSCache.back().m_IPAddresses = existingSameHost->m_IPAddresses;
                            hasIp = existingSameHost->m_IPAddresses.size() > 0;
                        }
                    }
                }

                if (fetchAdditional) {
                    if (!hasTxt && result->instance_name) {
                        if (BeginAsyncQuery(result->instance_name, MDNS_TYPE_TXT, 5000, 1)) {
                            ESP_LOGI(TAG, "Did not receive TXT records for instance %s, starting additional query to fetch them", result->instance_name);
                        }
                        else {
                            ESP_LOGI(TAG, "Did not receive TXT records for instance %s, but an additional query is already active to fetch them", result->instance_name);
                        }
                    }
                    if (!hasIp && result->hostname) {
                        if (BeginAsyncQuery(result->hostname, MDNS_TYPE_A, 5000, 1)) {
                            ESP_LOGI(TAG, "Did not receive IP address for hostname %s, starting additional query to fetch it", result->hostname);
                        }
                        else {
                            ESP_LOGI(TAG, "Did not receive IP address for hostname %s, but an additional query is already active to fetch it", result->hostname);
                        }
                    }
                }
            }

            ESP_LOGI(TAG, "mDNS cache size after update: %zu", m_DNSCache.size());
        }

        UpdateBrowseResultsAsync();
    }

    const ServiceDiscovery::Result* ServiceDiscovery::FindAnyResultByHostName(const std::string& hostName) {
        auto find = std::find_if(
            m_DNSCache.begin(),
            m_DNSCache.end(),
            [&hostName](const Result& entry) {
                return entry.m_HostName == hostName;
            }
        );
        return find != m_DNSCache.end() ? &(*find) : nullptr;
    }

    bool ServiceDiscovery::BeginAsyncQuery(const char* instanceName, uint16_t type, uint32_t timeout, size_t maxResults) {
        std::lock_guard lock(m_AdditionalQueryMutex);
        if (IsAsyncQueryActive(instanceName, type)) {
            return false;
        }

        mdns_search_once_t* searchHandle = mdns_query_async_new(instanceName, m_ServiceType.c_str(), m_ProtocolStr.c_str(), type, timeout, maxResults, GlobalAsyncResultNotifyCallback);
        if (searchHandle) {
            m_ActiveAdditionalQueries.push_back({ searchHandle, instanceName, type });
            {
                std::lock_guard lock(g_GlobalAsyncQueryMutex);
                g_AsyncQueryToInstanceMap[searchHandle] = this;
            }
            return true;
        }
        return false;
    }

    bool ServiceDiscovery::IsAsyncQueryActive(const char* instanceName, uint16_t type) const {
        return std::any_of(
            m_ActiveAdditionalQueries.begin(),
            m_ActiveAdditionalQueries.end(),
            [instanceName, type](const AdditionalQueryState& query) {
                return query.m_InstanceName == instanceName && query.m_Type == type;
            }
        );
    }

    void ServiceDiscovery::HandleAsyncQueryResults(mdns_search_once_t* searchHandle) {
        m_EventQueue.Post(
            [this, searchHandle]() {
                mdns_result_t* results = nullptr;
                uint8_t count;
                if (mdns_query_async_get_results(searchHandle, 0, &results, &count)) {
                    ESP_LOGI(TAG, "Received %u results for async query", count);
                    while (results) {
                        HandleBrowseResult(results, false);
                        results = results->next;
                    }
                }
                else {
                    ESP_LOGE(TAG, "Unexpected state - search results should be available after notification.");
                }

                {
                    std::lock_guard lock(m_AdditionalQueryMutex);

                    std::erase_if(
                        m_ActiveAdditionalQueries,
                        [searchHandle](const AdditionalQueryState& query) {
                            return query.m_SearchHandle == searchHandle;
                        }
                    );
                }

                mdns_query_async_delete(searchHandle);
            }
        );
    }

    void ServiceDiscovery::UpdateBrowseResultsAsync() {
        m_EventQueue.Post(
            [this]() {
                std::scoped_lock lock(m_BrowseStateMutex, m_DNSCacheMutex);

                for (auto& browse : m_ActiveBrowses) {
                    if (UpdateBrowseFromCache(browse)) {
                        browse.m_Callback(ResultSetAccessor(browse.m_LastResults));
                    }
                }
            },
            EVENT_TAG_UPDATE_BROWSE_RESULTS
        );
    }

    bool ServiceDiscovery::UpdateBrowseFromCache(QueryBrowseState& browse) {
        // first, update results that are already in the result set (match by instance name only)
        bool changed = false;
        for (size_t i = 0; i < browse.m_LastResults.size();) {
            auto& result = browse.m_LastResults[i];
            auto find = std::find_if(
                m_DNSCache.begin(),
                m_DNSCache.end(),
                [&result](const Result& cacheEntry) {
                    return result.m_InstanceName == cacheEntry.m_InstanceName;
                }
            );
            if (find == m_DNSCache.end() || !MatchQueryResult(browse.m_Query, result)) {
                ESP_LOGI(TAG, "Removing browse result for instance %s from query", result.m_InstanceName.c_str());
                // result disappeared or no longer matches query, remove it
                browse.m_LastResults.erase(browse.m_LastResults.begin() + i);
                changed = true;
            }
            else {
                // update result from cache (to get updated IP addresses and TXT records)
                if (result != *find) {
                    ESP_LOGI(TAG, "Updating browse result for instance %s based on DNS changes", result.m_InstanceName.c_str());
                    result = *find;
                    changed = true;
                }
                i++;
            }
        }
        // then, add new results from cache that match the query and aren't already in the result set
        for (auto&& newResult : m_DNSCache) {
            if (MatchQueryResult(browse.m_Query, newResult)) {
                if (std::none_of(
                    browse.m_LastResults.begin(),
                    browse.m_LastResults.end(),
                    [&newResult](const Result& result) {
                        return result.m_InstanceName == newResult.m_InstanceName;
                    }
                )) {
                    ESP_LOGI(TAG, "Adding browse result for instance %s from DNS to query results", newResult.m_InstanceName.c_str());
                    browse.m_LastResults.push_back(newResult);
                    changed = true;
                }
            }
        }
        return changed;
    }

    bool ServiceDiscovery::MatchQueryResult(const Query& query, const Result& result)
    {
        if (query.m_RequireIP && result.m_IPAddresses.empty()) {
            return false;
        }

        if (query.m_InstanceName.has_value()) {
            auto&& pattern = query.m_InstanceName.value();
            bool startWildcard = pattern.starts_with('*');
            bool endWildcard = pattern.ends_with('*');
            if (startWildcard && endWildcard) {
                if (!result.m_InstanceName.contains(pattern.substr(1, pattern.size() - 2))) {
                    return false;
                }
            }
            else if (startWildcard) {
                if (!result.m_InstanceName.ends_with(pattern.substr(1))) {
                    return false;
                }
            }
            else if (endWildcard) {
                if (!result.m_InstanceName.starts_with(pattern.substr(0, pattern.size() - 1))) {
                    return false;
                }
            }
            else {
                if (result.m_InstanceName != pattern) {
                    return false;
                }
            }
        }

        // check that all txt records in query have a matching value. this is inverted as there can be
        // multiple instances of the same key in the query, which express OR semantics.
        for (auto&& [key, value] : result.m_TxtRecords) {
            bool keyExists = false;
            bool valueMatches = false;

            for (auto&& [queryKey, queryValue] : query.m_TxtRecords) {
                if (queryKey == key) {
                    keyExists = true;
                    if (queryValue == value) {
                        valueMatches = true;
                        break;
                    }
                }
            }

            if (keyExists && !valueMatches) {
                return false;
            }
        }
        // now check that all TXT keys from the query exist in the result at least once.
        for (auto&& [queryKey, _] : query.m_TxtRecords) {
            bool found = false;
            for (auto&& [key, value] : result.m_TxtRecords) {
                if (key == queryKey) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    const char* ServiceDiscovery::ProtocolToAddressString(Protocol protocol)
    {
        switch (protocol)
        {
        case Protocol::TCP:
            return "_tcp";
        case Protocol::UDP:
            return "_udp";
        default:
            return "";
        }
    }

    std::string ServiceDiscovery::BuildMdnsAddress(const std::string& serviceType, Protocol protocol)
    {
        return BuildMdnsAddress(serviceType, ProtocolToAddressString(protocol));
    }

    std::string ServiceDiscovery::BuildMdnsAddress(const std::string& serviceType, const std::string& protocolStr)
    {
        return serviceType + "." + protocolStr + ".local";
    }

    ServiceDiscovery HttpServiceDiscovery()
    {
        return ServiceDiscovery("ibisip_http", ServiceDiscovery::Protocol::TCP);
    }
}