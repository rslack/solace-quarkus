package com.solace.quarkus.deployment.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

/**
 * quarkus-oidc-client is an optional dependency, so it is absent here: configuring OAuth2 must fail the build.
 */
public class SolaceOAuthWithoutOidcClientTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.solace.authentication.scheme", "AUTHENTICATION_SCHEME_OAUTH2")
            .overrideConfigKey("quarkus.solace.devservices.enabled", "false")
            .overrideConfigKey("quarkus.solace.host", "tcp://localhost:55555")
            .overrideConfigKey("quarkus.solace.vpn", "default")
            .assertException(t -> assertThat(t).hasStackTraceContaining(
                    "ConfigurationException: quarkus.solace.authentication.scheme=AUTHENTICATION_SCHEME_OAUTH2 requires the quarkus-oidc-client extension"));

    @Test
    void buildFails() {
        fail("Build should have failed");
    }
}
