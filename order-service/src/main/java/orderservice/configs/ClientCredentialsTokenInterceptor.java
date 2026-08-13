package orderservice.configs;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

import java.io.IOException;

/**
 * Attaches a {@code client_credentials} bearer token to every outbound call made through the
 * {@code RestClient} it is registered on.
 *
 * <p>inventory-service and payment-service both require {@code ADMIN} or {@code SERVICE} on every
 * endpoint. This service authenticates as {@code webstore-service-client}, whose registration in
 * auth-service carries {@code ROLE_SERVICE} — see {@code auth-service/CLAUDE.md}.
 *
 * <p><b>Tokens are cached and refreshed by the manager, not here.</b>
 * {@link OAuth2AuthorizedClientManager#authorize} returns the stored token while it is valid and
 * fetches a new one when it expires, so this runs one HTTP round-trip to auth-service roughly every
 * token lifetime — not once per outbound call.
 *
 * <p><b>Why not the caller's own token.</b> These calls also happen off any request thread: the
 * create-order saga's compensations run in their own transactions, and a retry can originate from a
 * scheduled job. Forwarding the end user's token would fail there, and would also mean a
 * {@code CUSTOMER} token reaching inventory-service, which requires {@code ADMIN} / {@code SERVICE}.
 * A service identity is the correct one for these hops.
 *
 * <p><b>The trade-off that comes with it:</b> inventory-service and payment-service see
 * "some webstore service", never "order-service specifically", because every service shares the one
 * client registration. Narrowing that means one registered client per calling service — auth-service's
 * {@code settings.client.role} lookup already supports it without a code change.
 */
public class ClientCredentialsTokenInterceptor implements ClientHttpRequestInterceptor {

    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final String clientRegistrationId;

    public ClientCredentialsTokenInterceptor(OAuth2AuthorizedClientManager authorizedClientManager,
                                             String clientRegistrationId) {
        this.authorizedClientManager = authorizedClientManager;
        this.clientRegistrationId = clientRegistrationId;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request,
                                        byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        // The principal name is required by the API but carries no meaning under
        // client_credentials — there is no user. It becomes the key the authorized-client
        // service stores the token under, so a constant keeps one shared cache entry.
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId(clientRegistrationId)
                .principal(clientRegistrationId)
                .build();

        OAuth2AuthorizedClient authorizedClient = authorizedClientManager.authorize(authorizeRequest);

        // Null means the token request itself failed — auth-service down, wrong secret, unknown
        // client. Failing here is deliberate: sending the request unauthenticated would surface as
        // a 401 from the downstream service, which order-service maps to a domain exception
        // (NotEnoughStockException, PaymentFailedException) and reports as a business failure.
        if (authorizedClient == null) {
            throw new IllegalStateException(
                    "Could not obtain a client_credentials token for registration '"
                            + clientRegistrationId + "' — check auth-service availability and "
                            + "the auth_client_secret value");
        }

        request.getHeaders().setBearerAuth(authorizedClient.getAccessToken().getTokenValue());
        return execution.execute(request, body);
    }
}
