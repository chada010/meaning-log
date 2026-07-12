package com.chad.meaninglog.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTests {

    @Test
    void directRequestIgnoresSpoofedForwardedForHeader() {
        MockHttpServletRequest request = request("203.0.113.20", "198.51.100.7");

        String clientIp = new ClientIpResolver("10.0.0.0/8").resolve(request);

        assertThat(clientIp).isEqualTo("203.0.113.20");
    }

    @Test
    void trustedProxyResolvesRightmostUntrustedAddressFromForwardedChain() {
        MockHttpServletRequest request = request("10.0.0.12", "198.51.100.7, 10.0.0.11");

        String clientIp = new ClientIpResolver("10.0.0.0/8").resolve(request);

        assertThat(clientIp).isEqualTo("198.51.100.7");
    }

    @Test
    void trustedProxyFallsBackToRemoteAddressWhenForwardedHeaderIsInvalid() {
        MockHttpServletRequest request = request("10.0.0.12", "not-an-ip");

        String clientIp = new ClientIpResolver("10.0.0.0/8").resolve(request);

        assertThat(clientIp).isEqualTo("10.0.0.12");
    }

    @Test
    void emptyTrustedProxyConfigurationKeepsLocalDirectRequestsWorking() {
        MockHttpServletRequest request = request("127.0.0.1", "198.51.100.7");

        String clientIp = new ClientIpResolver("").resolve(request);

        assertThat(clientIp).isEqualTo("127.0.0.1");
    }

    private MockHttpServletRequest request(String remoteAddress, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }
}
