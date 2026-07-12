package com.chad.meaninglog.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
public class ClientIpResolver {

    private final List<TrustedNetwork> trustedProxyNetworks;

    public ClientIpResolver(@Value("${app.trusted-proxy-cidrs:}") String trustedProxyCidrs) {
        this.trustedProxyNetworks = parseTrustedNetworks(trustedProxyCidrs);
    }

    public String resolve(HttpServletRequest request) {
        InetAddress remoteAddress = parseAddress(request.getRemoteAddr());
        if (remoteAddress == null || !isTrusted(remoteAddress)) {
            return request.getRemoteAddr();
        }

        return resolveForwardedAddress(request.getHeader("X-Forwarded-For"))
                .orElseGet(remoteAddress::getHostAddress);
    }

    private Optional<String> resolveForwardedAddress(String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return Optional.empty();
        }

        List<InetAddress> addresses = new ArrayList<>();
        for (String value : forwardedFor.split(",")) {
            InetAddress address = parseAddress(value.trim());
            if (address == null) {
                return Optional.empty();
            }
            addresses.add(address);
        }

        for (int index = addresses.size() - 1; index >= 0; index--) {
            InetAddress address = addresses.get(index);
            if (!isTrusted(address)) {
                return Optional.of(address.getHostAddress());
            }
        }
        return Optional.empty();
    }

    private boolean isTrusted(InetAddress address) {
        return trustedProxyNetworks.stream().anyMatch(network -> network.contains(address));
    }

    private List<TrustedNetwork> parseTrustedNetworks(String trustedProxyCidrs) {
        if (trustedProxyCidrs == null || trustedProxyCidrs.isBlank()) {
            return List.of();
        }
        return Arrays.stream(trustedProxyCidrs.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(TrustedNetwork::parse)
                .toList();
    }

    private static InetAddress parseAddress(String value) {
        if (value == null || value.isBlank() || !isIpLiteral(value)) {
            return null;
        }
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    private static boolean isIpLiteral(String value) {
        return value.matches("[0-9.]+") || (value.contains(":") && value.matches("[0-9a-fA-F:.]+"));
    }

    private record TrustedNetwork(InetAddress networkAddress, int prefixLength) {

        static TrustedNetwork parse(String value) {
            String[] parts = value.split("/", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Trusted proxy CIDR must include a prefix length: " + value);
            }

            InetAddress networkAddress = parseAddress(parts[0]);
            if (networkAddress == null) {
                throw new IllegalArgumentException("Trusted proxy CIDR must contain an IP address: " + value);
            }

            try {
                int prefixLength = Integer.parseInt(parts[1]);
                int maxPrefixLength = networkAddress.getAddress().length * Byte.SIZE;
                if (prefixLength < 0 || prefixLength > maxPrefixLength) {
                    throw new IllegalArgumentException("Trusted proxy CIDR prefix length is invalid: " + value);
                }
                return new TrustedNetwork(networkAddress, prefixLength);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Trusted proxy CIDR prefix length is invalid: " + value, ex);
            }
        }

        boolean contains(InetAddress address) {
            byte[] networkBytes = networkAddress.getAddress();
            byte[] addressBytes = address.getAddress();
            if (networkBytes.length != addressBytes.length) {
                return false;
            }

            int fullBytes = prefixLength / Byte.SIZE;
            int remainingBits = prefixLength % Byte.SIZE;
            for (int index = 0; index < fullBytes; index++) {
                if (networkBytes[index] != addressBytes[index]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }

            int mask = 0xFF << (Byte.SIZE - remainingBits);
            return (networkBytes[fullBytes] & mask) == (addressBytes[fullBytes] & mask);
        }
    }
}
