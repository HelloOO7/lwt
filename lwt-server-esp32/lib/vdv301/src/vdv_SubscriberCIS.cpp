#include "vdv_SubscriberCIS.h"

#include <iostream>

namespace vdv301
{
    SubscriberCIS::SubscriberCIS(ServiceDiscovery& sd) :
        SubscriberHttp(
            sd,
            "CustomerInformationService",
            ServiceDiscovery::QueryBuilder()
            .FilterInstanceName("CustomerInformationService_ropid_vdv301tester_2_3cz1_0")
            .FilterTxtRecord("ver", "2.3CZ1.0")
            .Build()
        )
    {

    }

    void SubscriberCIS::OnSubscribe()
    {
        SubscribeToOperation("SubscribeAllData");
    }

    void SubscriberCIS::OnOperationResult(const std::string& operation, const std::string& result)
    {
        if (operation == "SubscribeAllData") {
            std::cout << "Received SubscribeAllData result: " << result << std::endl;
        }
        else {
            std::cout << "Unexpected operation: " << operation << std::endl;
        }
    }
}