package com.portscape.risk.nvd;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CpeNameTest {

    @Test
    void convertsTheFormatNmapEmitsToTheOneNvdAccepts() {
        assertThat(CpeName.toVersionedCpe23("cpe:/a:openbsd:openssh:9.6"))
                .contains("cpe:2.3:a:openbsd:openssh:9.6:*:*:*:*:*:*:*");
    }

    @Test
    void keepsExtraComponentsWhenTheyExist() {
        assertThat(CpeName.toVersionedCpe23("cpe:/a:apache:http_server:2.4.58:p1"))
                .contains("cpe:2.3:a:apache:http_server:2.4.58:p1:*:*:*:*:*:*");
    }

    @Test
    void handlesOperatingSystemCpes() {
        assertThat(CpeName.toVersionedCpe23("cpe:/o:linux:linux_kernel:5.15"))
                .contains("cpe:2.3:o:linux:linux_kernel:5.15:*:*:*:*:*:*:*");
    }

    @Test
    @DisplayName("um CPE sem versao e recusado: casaria com todos os CVEs do produto")
    void rejectsCpesWithoutAVersion() {
        assertThat(CpeName.toVersionedCpe23("cpe:/a:busybox:busybox")).isEmpty();
        assertThat(CpeName.toVersionedCpe23("cpe:/a:busybox:busybox:")).isEmpty();
        assertThat(CpeName.toVersionedCpe23("cpe:/a:busybox:busybox:*")).isEmpty();
        assertThat(CpeName.toVersionedCpe23("cpe:/a:busybox:busybox:-")).isEmpty();
    }

    @Test
    void rejectsGarbage() {
        assertThat(CpeName.toVersionedCpe23(null)).isEmpty();
        assertThat(CpeName.toVersionedCpe23("")).isEmpty();
        assertThat(CpeName.toVersionedCpe23("openssh 9.6")).isEmpty();
        assertThat(CpeName.toVersionedCpe23("cpe:2.3:a:openbsd:openssh:9.6")).isEmpty();
    }

    @Test
    @DisplayName("os termos de pesquisa sao o primeiro token do produto mais a versao")
    void buildsSearchTermsFromTheFirstProductToken() {
        // "dropbear_ssh_server 2017.75" devolve zero no NVD; "dropbear 2017.75" acerta.
        assertThat(CpeName.toSearchTerms("cpe:/a:matt_johnston:dropbear_ssh_server:2017.75"))
                .contains("dropbear 2017.75");
        assertThat(CpeName.toSearchTerms("cpe:/a:igor_sysoev:nginx:1.27.5"))
                .contains("nginx 1.27.5");
        assertThat(CpeName.toSearchTerms("cpe:/a:openbsd:openssh:9.6"))
                .contains("openssh 9.6");
    }

    @Test
    void hasNoSearchTermsForACpeItWouldRejectAnyway() {
        assertThat(CpeName.toSearchTerms("cpe:/a:busybox:busybox")).isEmpty();
        assertThat(CpeName.toSearchTerms("lixo")).isEmpty();
    }

    @Test
    void exposesTheProductAndVersionTheNmapReported() {
        assertThat(CpeName.productOf("cpe:/a:matt_johnston:dropbear_ssh_server:2017.75"))
                .contains("dropbear_ssh_server");
        assertThat(CpeName.versionOf("cpe:/a:matt_johnston:dropbear_ssh_server:2017.75"))
                .contains("2017.75");
    }
}
