package de.svws_nrw.edugate.core.svws;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

/**
 * SSRF-Schutz für {@link HttpSvwsConnectionTester} (Issue #17): entscheidet, ob ein
 * aufgelöster Ziel-Host für einen Verbindungstest erlaubt ist.
 *
 * <p>Ohne explizite Freigabe werden Loopback-, Link-Local-, Multicast- und die üblichen
 * privaten/reservierten Adressbereiche (RFC 1918, RFC 6598 Carrier-Grade-NAT, IPv6-ULA,
 * Cloud-Metadata-Bereich {@code 169.254.0.0/16} u. a.) abgelehnt - ein kompromittierter oder
 * fehlerhaft konfigurierter {@code svws_instanz.base_url}-Wert darf nicht dazu missbraucht
 * werden können, interne Infrastruktur (Datenbank, Keycloak, Cloud-Metadata-Endpunkt, ...) über
 * die Netzposition der Control Plane zu erreichen. Zulässige SVWS-Zielnetze (typischerweise
 * selbst private Adressen gemäß ADR-007-Netzzonenkonzept) werden über eine explizite
 * CIDR-Allowlist freigeschaltet ({@link #withAllowedNetworks(List)}).
 *
 * <p><b>Bekannte Grenze:</b> Die Prüfung erfolgt anhand der zum Zeitpunkt der Validierung
 * aufgelösten Adresse(n); der eigentliche HTTP-Request des JDK-{@code HttpClient} löst den
 * Hostnamen anschließend selbst erneut auf. Ein DNS-Rebinding zwischen diesen beiden
 * Auflösungen (TOCTOU) ist mit Bordmitteln des JDK-HttpClient nicht vollständig auszuschließen,
 * da dieser keine Möglichkeit bietet, eine bereits aufgelöste Adresse fest an einen einzelnen
 * Request zu binden. Die Validierung schließt damit den naheliegenden Angriffsweg (SVWS-Instanz
 * direkt auf eine interne Adresse zeigen lassen), nicht aber einen zeitlich exakt getakteten
 * DNS-Rebinding-Angriff.
 */
public final class SvwsTargetGuard {

    private static final List<IpRange> DEFAULT_BLOCKED = List.of(
        IpRange.parse("0.0.0.0/8"),
        IpRange.parse("10.0.0.0/8"),
        IpRange.parse("100.64.0.0/10"),
        IpRange.parse("127.0.0.0/8"),
        IpRange.parse("169.254.0.0/16"),
        IpRange.parse("172.16.0.0/12"),
        IpRange.parse("192.0.0.0/24"),
        IpRange.parse("192.0.2.0/24"),
        IpRange.parse("192.168.0.0/16"),
        IpRange.parse("198.18.0.0/15"),
        IpRange.parse("198.51.100.0/24"),
        IpRange.parse("203.0.113.0/24"),
        IpRange.parse("224.0.0.0/4"),
        IpRange.parse("240.0.0.0/4"),
        IpRange.parse("255.255.255.255/32"),
        IpRange.parse("::1/128"),
        IpRange.parse("fe80::/10"),
        IpRange.parse("fc00::/7"),
        IpRange.parse("ff00::/8"));

    private final List<IpRange> allowedNetworks;

    private SvwsTargetGuard(final List<IpRange> allowedNetworks) {
        this.allowedNetworks = List.copyOf(allowedNetworks);
    }

    /** Blockiert alle in {@link #DEFAULT_BLOCKED} gelisteten Sonder-Adressbereiche, keine Ausnahmen. */
    public static SvwsTargetGuard defaultDeny() {
        return new SvwsTargetGuard(List.of());
    }

    /** Erlaubt jedes Ziel - nur für lokale Entwicklung/Tests, niemals in Produktion. */
    public static SvwsTargetGuard allowAll() {
        return withAllowedNetworks(List.of("0.0.0.0/0", "::/0"));
    }

    /**
     * @param cidrs zusätzlich erlaubte Adressbereiche (CIDR-Notation), die auch dann akzeptiert
     *      werden, wenn sie in {@link #DEFAULT_BLOCKED} liegen - typischerweise das tatsächliche
     *      SVWS-Zielnetz eines Betreibers.
     */
    public static SvwsTargetGuard withAllowedNetworks(final List<String> cidrs) {
        return new SvwsTargetGuard(cidrs.stream().map(IpRange::parse).toList());
    }

    /**
     * Löst {@code host} auf und prüft <b>alle</b> zurückgegebenen Adressen (nicht nur die
     * erste) - ein DNS-Server könnte sonst eine erlaubte Adresse zuerst und eine blockierte
     * Adresse später liefern.
     *
     * @return {@code true}, wenn der Host sich auflösen ließ und keine der Adressen blockiert ist.
     */
    public boolean isAllowed(final String host) {
        final InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (final UnknownHostException e) {
            return false;
        }
        if (addresses.length == 0) {
            return false;
        }
        return Arrays.stream(addresses).allMatch(this::isAllowedAddress);
    }

    private boolean isAllowedAddress(final InetAddress rawAddress) {
        final InetAddress address = unwrapIfIpv4Mapped(rawAddress);
        if (allowedNetworks.stream().anyMatch(range -> range.contains(address))) {
            return true;
        }
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        return DEFAULT_BLOCKED.stream().noneMatch(range -> range.contains(address));
    }

    /**
     * Entpackt IPv4-mapped IPv6-Adressen ({@code ::ffff:a.b.c.d}) zur eingebetteten IPv4-Adresse,
     * damit sie gegen die IPv4-CIDR-Bereiche geprüft werden - sonst könnten IPv4-Sonderbereiche
     * (z. B. Loopback) über diese Schreibweise an der Prüfung vorbeigeschmuggelt werden.
     */
    private static InetAddress unwrapIfIpv4Mapped(final InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return address;
        }
        final byte[] bytes = address.getAddress();
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return address;
            }
        }
        if ((bytes[10] & 0xFF) != 0xFF || (bytes[11] & 0xFF) != 0xFF) {
            return address;
        }
        try {
            return InetAddress.getByAddress(Arrays.copyOfRange(bytes, 12, 16));
        } catch (final UnknownHostException e) {
            return address;
        }
    }
}
