package second.b.main;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpSwitchRequestHandler implements HttpHandler {
    private final boolean sessionAware;
    private final InetSocketAddress[] redirectServerAddresses;

    public HttpSwitchRequestHandler(final boolean sessionAware, final InetSocketAddress... redirectHostAddresses) {
        this.sessionAware = sessionAware;
        this.redirectServerAddresses = redirectHostAddresses;
    }


    /**
     * Returns the session awareness.
     *
     * @return the session awareness
     */
    public boolean getSessionAware() {
        return this.sessionAware;
    }


    /**
     * Returns the redirect server addresses.
     *
     * @return the redirect server addresses
     */
    public InetSocketAddress[] getRedirectServerAddresses() {
        return this.redirectServerAddresses;
    }


    /**
     * Selects a redirect server address corresponding to the given client address.
     *
     * @param clientAddress the client address
     * @return the selected redirect server address
     */
    public InetSocketAddress selectRedirectServerAddress(final InetAddress clientAddress) {
        final int index;
        if (this.getSessionAware()) {
            index = new Random(clientAddress.hashCode()).nextInt(this.redirectServerAddresses.length);
        } else {
            index = ThreadLocalRandom.current().nextInt(this.redirectServerAddresses.length);
        }
        return this.redirectServerAddresses[index];
    }

    /**
     * Handles the given HTTP exchange by redirecting the request.
     *
     * @param exchange the HTTP exchange
     * @throws NullPointerException if the given exchange is {@code null}
     * @throws IOException          if there is an I/O related problem
     */
    @Override
    public void handle(final HttpExchange exchange) throws IOException {
        try (exchange) {
            final InetSocketAddress redirectServerAddress =
                    this.selectRedirectServerAddress(exchange.getRemoteAddress().getAddress());

            final URI requestURI = exchange.getRequestURI();
            final URI redirectURI;
            try {
                redirectURI = new URI(requestURI.getScheme(), requestURI.getUserInfo(), redirectServerAddress.getHostName()
                        , redirectServerAddress.getPort(), requestURI.getPath(), requestURI.getQuery(), requestURI.getFragment());
            } catch (URISyntaxException e) {
                throw new AssertionError(e);
            }

            exchange.getResponseHeaders().set("Location", redirectURI.toASCIIString());
            exchange.sendResponseHeaders(307, 0);

            Logger.getGlobal().log(Level.INFO, "Redirected request for \"{0}\" to \"{1}\".",
                    new URI[]{requestURI, redirectURI});
        }
    }
}

