package com.chad.meaninglog.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class ClientAddressResolver {

    private static final Pattern IPV4_LITERAL = Pattern.compile("\\d{1,3}(?:\\.\\d{1,3}){3}");
    private static final Pattern IPV6_LITERAL = Pattern.compile("[0-9a-fA-F:.]+");

    private final List<CidrRange> trustedProxyRanges;

    public ClientAddressResolver(@Value("${auth.trusted-proxy-cidrs:}") String trustedProxyCidrs) {
        this.trustedProxyRanges = parseTrustedProxyRanges(trustedProxyCidrs);
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        InetAddress remoteIp = parseAddress(remoteAddress);
        if (remoteIp == null || !isTrustedProxy(remoteIp)) {
            return remoteAddress;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddress;
        }

        String[] addresses = forwardedFor.split(",");
        for (int index = addresses.length - 1; index >= 0; index--) {
            InetAddress address = parseAddress(addresses[index].trim());
            if (address != null && !isTrustedProxy(address)) {
                return address.getHostAddress();
            }
        }
        return remoteAddress;
    }

    private List<CidrRange> parseTrustedProxyRanges(String trustedProxyCidrs) {
        List<CidrRange> ranges = new ArrayList<>();
        for (String cidr : trustedProxyCidrs.split(",")) {
            if (!cidr.isBlank()) {
                ranges.add(CidrRange.parse(cidr.trim()));
            }
        }
        return List.copyOf(ranges);
    }

    private boolean isTrustedProxy(InetAddress address) {
        return trustedProxyRanges.stream().anyMatch(range -> range.contains(address));
    }

    private static InetAddress parseAddress(String value) {
        if (value.isBlank() || !(IPV4_LITERAL.matcher(value).matches() || IPV6_LITERAL.matcher(value).matches())) {
            return null;
        }
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    private record CidrRange(byte[] address, int prefixLength) {

        static CidrRange parse(String cidr) {
            String[] parts = cidr.split("/", -1);
            InetAddress address = parts.length == 2 ? parseAddress(parts[0]) : null;
            if (address == null) {
                throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + cidr);
            }
            try {
                int prefixLength = Integer.parseInt(parts[1]);
                if (prefixLength < 0 || prefixLength > address.getAddress().length * Byte.SIZE) {
                    throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + cidr);
                }
                return new CidrRange(address.getAddress(), prefixLength);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + cidr, ex);
            }
        }

        boolean contains(InetAddress candidate) {
            byte[] candidateAddress = candidate.getAddress();
            if (candidateAddress.length != address.length) {
                return false;
            }
            int wholeBytes = prefixLength / Byte.SIZE;
            for (int index = 0; index < wholeBytes; index++) {
                if (address[index] != candidateAddress[index]) {
                    return false;
                }
            }
            int remainingBits = prefixLength % Byte.SIZE;
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (Byte.SIZE - remainingBits);
            return (address[wholeBytes] & mask) == (candidateAddress[wholeBytes] & mask);
        }
    }
}
