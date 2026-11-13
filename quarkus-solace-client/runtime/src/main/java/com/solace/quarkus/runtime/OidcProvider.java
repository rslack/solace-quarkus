package com.solace.quarkus.runtime;

import static com.solace.messaging.config.SolaceProperties.AuthenticationProperties.SCHEME_OAUTH2_ACCESS_TOKEN;

import java.time.Duration;
import java.util.Optional;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.solace.messaging.MessagingService;

import io.quarkus.logging.Log;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.OidcClients;
import io.quarkus.oidc.client.Tokens;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;

/**
 * Registered as a bean only when the {@code quarkus-oidc-client} extension is present.
 */
public class OidcProvider {
    @ConfigProperty(name = "quarkus.solace.oidc.refresh.interval", defaultValue = "60s")
    Duration duration;

    @ConfigProperty(name = "quarkus.solace.oidc.refresh.timeout", defaultValue = "10s")
    Duration refreshTimeout;

    /**
     * Name of the <a href="https://quarkus.io/guides/security-openid-connect-client-reference">Quarkus OIDC client</a>
     * that obtains the broker's OAuth2 access token.
     * Named clients are only needed when identity providers or credentials differ from the default one.
     */
    @ConfigProperty(name = "quarkus.solace.oidc.client-name")
    Optional<String> oidcClientName;

    @Inject
    OidcClients clients;

    private volatile Tokens lastToken;

    Tokens getToken() {
        OidcClient client = getClient();
        Tokens firstToken = client.getTokens().await().indefinitely();
        lastToken = firstToken;
        return firstToken;
    }

    void init(MessagingService service) {
        OidcClient client = getClient();
        Multi.createFrom().ticks().every(duration)
                .onOverflow().drop()
                .emitOn(Infrastructure.getDefaultWorkerPool())
                .call(() -> {
                    if (lastToken != null && lastToken.getRefreshToken() != null
                            && lastToken.isAccessTokenWithinRefreshInterval()) {
                        Log.info("Refreshing access token for Solace connection");
                        return client.refreshTokens(lastToken.getRefreshToken()).invoke(tokens -> lastToken = tokens).ifNoItem()
                                .after(refreshTimeout).fail();
                    } else {
                        Log.info("Acquiring access token for Solace connection");
                        return client.getTokens().invoke(tokens -> lastToken = tokens).ifNoItem().after(refreshTimeout).fail();
                    }
                })
                .onFailure().call(t -> {
                    Log.error("Failed to acquire access token for Solace connection", t);
                    // ignore the refresh
                    return Uni.createFrom().voidItem();
                })
                .subscribe().with(x -> {
                    if (service.isConnected()) {
                        service.updateProperty(SCHEME_OAUTH2_ACCESS_TOKEN, lastToken.getAccessToken());
                        Log.info("Updated Solace Session with latest access token");
                    } else {
                        Log.info("Solace service is not connected, cannot update access token without valid connection");
                    }
                });
    }

    OidcClient getClient() {
        return oidcClientName.map(clients::getClient)
                .orElseGet(clients::getClient);
    }

}
