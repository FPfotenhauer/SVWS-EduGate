package de.svws_nrw.edugate.core.svws;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Ein IPv4- oder IPv6-Adressbereich in CIDR-Notation (z. B. {@code 10.0.0.0/8}), verwendet von
 * {@link SvwsTargetGuard}. Nimmt ausschließlich numerische IP-Literale entgegen - niemals
 * Hostnamen -, damit weder beim Parsen fester Bereiche noch bei einer Betreiberkonfiguration
 * unbeabsichtigt ein DNS-Lookup ausgelöst wird.
 */
final class IpRange {

    private final byte[] network;
    private final int prefixLength;

    private IpRange(final byte[] network, final int prefixLength) {
        this.network = network;
        this.prefixLength = prefixLength;
    }

    static IpRange parse(final String cidr) {
        final String trimmed = cidr.trim();
        final int slash = trimmed.indexOf('/');
        if (slash < 0) {
            throw new IllegalArgumentException("Kein CIDR-Bereich (fehlendes '/'): " + cidr);
        }
        final String addressPart = trimmed.substring(0, slash);
        final int prefixLength = Integer.parseInt(trimmed.substring(slash + 1));
        final byte[] address = parseLiteral(addressPart);
        final int maxPrefixLength = address.length * 8;
        if (prefixLength < 0 || prefixLength > maxPrefixLength) {
            throw new IllegalArgumentException("Ungültige Präfixlänge in CIDR-Bereich: " + cidr);
        }
        return new IpRange(maskToPrefix(address, prefixLength), prefixLength);
    }

    boolean contains(final InetAddress address) {
        final byte[] addressBytes = address.getAddress();
        if (addressBytes.length != network.length) {
            return false;
        }
        final int fullBytes = prefixLength / 8;
        final int remainingBits = prefixLength % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (addressBytes[i] != network[i]) {
                return false;
            }
        }
        if (remainingBits > 0) {
            final int mask = (0xFF << (8 - remainingBits)) & 0xFF;
            if ((addressBytes[fullBytes] & mask) != (network[fullBytes] & mask)) {
                return false;
            }
        }
        return true;
    }

    private static byte[] parseLiteral(final String literal) {
        try {
            return InetAddress.getByName(literal).getAddress();
        } catch (final UnknownHostException e) {
            throw new IllegalArgumentException("Kein gültiges IP-Literal: " + literal, e);
        }
    }

    private static byte[] maskToPrefix(final byte[] address, final int prefixLength) {
        final byte[] masked = address.clone();
        final int fullBytes = prefixLength / 8;
        final int remainingBits = prefixLength % 8;
        for (int i = fullBytes + (remainingBits > 0 ? 1 : 0); i < masked.length; i++) {
            masked[i] = 0;
        }
        if (remainingBits > 0) {
            final int mask = (0xFF << (8 - remainingBits)) & 0xFF;
            masked[fullBytes] = (byte) (masked[fullBytes] & mask);
        }
        return masked;
    }
}
