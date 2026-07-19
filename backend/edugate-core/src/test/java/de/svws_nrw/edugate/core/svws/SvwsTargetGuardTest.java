package de.svws_nrw.edugate.core.svws;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Testet den SSRF-Schutz aus {@link SvwsTargetGuard} (Issue #17). Verwendet ausschließlich
 * IP-Literale statt Hostnamen, damit keine Tests einen echten DNS-Lookup auslösen (unabhängig
 * von Netzwerkverfügbarkeit in der Testumgebung).
 */
class SvwsTargetGuardTest {

    @Test
    void defaultDeny_blockiertLoopback() {
        final SvwsTargetGuard guard = SvwsTargetGuard.defaultDeny();

        assertThat(guard.isAllowed("127.0.0.1")).isFalse();
        assertThat(guard.isAllowed("::1")).isFalse();
    }

    @Test
    void defaultDeny_blockiertRfc1918PrivateNetze() {
        final SvwsTargetGuard guard = SvwsTargetGuard.defaultDeny();

        assertThat(guard.isAllowed("10.1.2.3")).isFalse();
        assertThat(guard.isAllowed("172.16.5.5")).isFalse();
        assertThat(guard.isAllowed("192.168.1.1")).isFalse();
    }

    @Test
    void defaultDeny_blockiertLinkLocalUndCloudMetadata() {
        final SvwsTargetGuard guard = SvwsTargetGuard.defaultDeny();

        assertThat(guard.isAllowed("169.254.169.254")).isFalse();
        assertThat(guard.isAllowed("fe80::1")).isFalse();
    }

    @Test
    void defaultDeny_blockiertIpv6UniqueLocal() {
        final SvwsTargetGuard guard = SvwsTargetGuard.defaultDeny();

        assertThat(guard.isAllowed("fc00::1")).isFalse();
        assertThat(guard.isAllowed("fd12:3456:789a::1")).isFalse();
    }

    @Test
    void defaultDeny_blockiertIpv4MappedIpv6Loopback() {
        final SvwsTargetGuard guard = SvwsTargetGuard.defaultDeny();

        assertThat(guard.isAllowed("::ffff:127.0.0.1")).isFalse();
    }

    @Test
    void defaultDeny_erlaubtOeffentlicheAdresse() {
        final SvwsTargetGuard guard = SvwsTargetGuard.defaultDeny();

        assertThat(guard.isAllowed("8.8.8.8")).isTrue();
    }

    @Test
    void withAllowedNetworks_erlaubtNurFreigegebenesZielnetz() {
        final SvwsTargetGuard guard = SvwsTargetGuard.withAllowedNetworks(List.of("10.20.0.0/16"));

        assertThat(guard.isAllowed("10.20.5.5")).isTrue();
        assertThat(guard.isAllowed("10.30.5.5")).isFalse();
        assertThat(guard.isAllowed("127.0.0.1")).isFalse();
    }

    @Test
    void allowAll_erlaubtAuchLoopbackUndPrivateNetze() {
        final SvwsTargetGuard guard = SvwsTargetGuard.allowAll();

        assertThat(guard.isAllowed("127.0.0.1")).isTrue();
        assertThat(guard.isAllowed("10.1.2.3")).isTrue();
        assertThat(guard.isAllowed("::1")).isTrue();
    }
}
