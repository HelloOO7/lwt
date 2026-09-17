package cz.spojenka.lwt;

import javax.net.ssl.SSLContext;

import cz.spojenka.lwdn.LwdnMockClient;
import cz.spojenka.lwdn.LwdnScanner;
import cz.spojenka.lwdn.MockLwdnScanner;

public class CICOMockNetworkProvider implements ICICONetworkProvider {

    private final LwdnMockClient mockClient;

    public CICOMockNetworkProvider(LwdnMockClient mockClient) {
        this.mockClient = mockClient;
    }

    public LwdnMockClient getMockClient() {
        return mockClient;
    }

    @Override
    public LwdnScanner createLwdnScanner() {
        return new MockLwdnScanner(mockClient);
    }

    @Override
    public ICICODeviceClient createDeviceClient(LwtDevice device, SSLContext secureContext) {
        return new CICOMockDeviceClient(device, mockClient);
    }

    @Override
    public void close() {
        mockClient.close();
    }
}
