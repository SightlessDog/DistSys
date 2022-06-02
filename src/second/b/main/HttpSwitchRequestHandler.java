package second.b.main;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
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
        // TODO: return one of the redirectServerAddresses based on the sessionAware property
        // and the given client address
        if (getSessionAware()) {
            int port = Math.abs(new Random(clientAddress.hashCode()).nextInt() % 2);
            return new InetSocketAddress(clientAddress, port);
        } else {
            int random = Math.abs(ThreadLocalRandom.current().nextInt() % 2);
            return new InetSocketAddress(clientAddress, getRedirectServerAddresses()[random].getPort());
        }
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
            final InetSocketAddress redirectServerAddress = this.selectRedirectServerAddress(exchange.getRemoteAddress().getAddress());

            // TODO: create a redirect URI based on the exchange's request URI parts, and the redirect server address's
            // hostname and port. Set the response header "Location" of the exchange with this URI's
            // ASCII representation. Send the exchange's response headers using code 307 (temporary redirect)
            // and zero as reponse length. Note that the schema of the redirect URI will usually be null,
            // which works fine.
            System.out.println(redirectServerAddress.getHostName());
            final URI redirectURI = URI.create(redirectServerAddress.getHostName() + redirectServerAddress.getPort() + "/external/HelloWorld.html");
            final URI requestURI = exchange.getRequestURI();

            exchange.getResponseHeaders().set("Location", redirectURI.toASCIIString());
            exchange.sendResponseHeaders(307, 0);


            Logger.getGlobal().log(Level.INFO, "Redirected request for \"{0}\" to \"{1}\".", new URI[]{requestURI, redirectURI});
        }
    }
}

