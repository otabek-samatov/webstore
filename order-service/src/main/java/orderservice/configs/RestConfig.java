package orderservice.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestClient;

/**
 * The single {@code RestClient} every outbound call in this service goes through — the inventory
 * price lookup and stock reservation, and {@code PaymentClient}'s charge.
 *
 * <p>All three targets now require a token, so the client carries a
 * {@link ClientCredentialsTokenInterceptor} that authenticates this service as
 * {@code webstore-service-client}.
 */
@Configuration
public class RestConfig {

    /**
     * Must match a key under {@code spring.security.oauth2.client.registration.*} in the config
     * repo's {@code order-service.yml}. Kept identical to the {@code client-id} so the two cannot
     * drift apart confusingly; Spring does not require them to match.
     */
    private static final String CLIENT_REGISTRATION_ID = "webstore-service-client";

    /**
     * Obtains and caches {@code client_credentials} tokens.
     *
     * <p><b>{@code AuthorizedClientServiceOAuth2AuthorizedClientManager}, not the {@code Default}
     * one.</b> The default manager is bound to a servlet request and an {@code Authentication} in
     * the security context. Some of these calls run outside both — the create-order saga's
     * compensations execute in their own transactions, and {@code PaymentFailedReaper} drives status
     * changes from a scheduled thread. This variant works anywhere and stores the token in the
     * shared {@code OAuth2AuthorizedClientService} rather than per end user, which is what a service
     * identity should do.
     *
     * <p>Only the {@code clientCredentials()} provider is registered — this service never performs
     * an authorization-code or refresh-token flow on its own behalf.
     */
    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {

        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        clientRegistrationRepository, authorizedClientService);

        manager.setAuthorizedClientProvider(
                OAuth2AuthorizedClientProviderBuilder.builder()
                        .clientCredentials()
                        .build());

        return manager;
    }

    @Bean
    public RestClient restClient(OAuth2AuthorizedClientManager authorizedClientManager) {
        return RestClient.builder()
                .requestInterceptor(new ClientCredentialsTokenInterceptor(
                        authorizedClientManager, CLIENT_REGISTRATION_ID))
                .build();
    }
}
