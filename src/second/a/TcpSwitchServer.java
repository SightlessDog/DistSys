package second.a;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.sb.ds.util.Copyright;
import edu.sb.ds.util.InetAddresses;
import edu.sb.ds.util.Maps;
import edu.sb.ds.util.TcpServers;


/**
 * This class model TCP switch servers, i.e. "spray" servers for all kinds of TCP based
 * protocol connections. It redirects incoming client connections to it's given set of redirect
 * servers, either randomly selected, or determined by known session association. Note that while
 * this implementation routes all kinds of TCP protocols, a single instance is only able to route
 * one protocol type unless it's child servers support multi-protocol requests.<br />
 * Session association is determined by receiving subsequent requests from the same client, which
 * may or may not be interpreted as being part of the same session by the protocol server selected.
 * However, two requests can never be part of the same session if they do not share the same request
 * client address! Note that this algorithm allows for protocol independence, but does not work with
 * clients that dynamically change their IP-address during a session's lifetime.
 */
@Copyright(year=2008, holders="Sascha Baumeister")
public class TcpSwitchServer implements Runnable, AutoCloseable {
    static private final String PROPERTIES_FILE_NAME = "redirect-servers.properties";

    private final ExecutorService threadPool;
    private final ServerSocket host;
    final boolean sessionAware;
    final InetSocketAddress[] redirectServerAddresses;


    /**
     * Initializes a new instance.
     * @param servicePort the service port
     * @param sessionAware {@code true} if the server is aware of sessions, {@code false} otherwise
     * @param redirectHostAddresses the redirect host addresses
     * @throws NullPointerException if any of the given addresses is {@code null}
     * @throws IllegalArgumentException if the given service port is outside range [0, 0xFFFF], or
     *         the given socket-addresses array is empty
     * @throws IOException if the given port is already in use, or cannot be bound
     */
    public TcpSwitchServer (final int servicePort, final boolean sessionAware, final InetSocketAddress... redirectHostAddresses) throws IOException {
        if (redirectHostAddresses.length == 0) throw new IllegalArgumentException();

        this.threadPool = Executors.newCachedThreadPool();
        this.host = new ServerSocket(servicePort);
        this.sessionAware = sessionAware;
        this.redirectServerAddresses = redirectHostAddresses;
    }


    /**
     * Closes this server.
     * @throws IOException {@inheritDoc}
     */
    public void close () throws IOException {
        try {
            this.host.close();
        } finally {
            this.threadPool.shutdown();
        }
    }


    /**
     * Returns the thread pool.
     * @return the thread pool
     */
    public ExecutorService getThreadPool () {
        return this.threadPool;
    }


    /**
     * Returns the service port.
     * @return the service port
     */
    public int getServicePort () {
        return this.host.getLocalPort();
    }


    /**
     * Returns the session awareness.
     * @return the session awareness
     */
    public boolean getSessionAware () {
        return this.sessionAware;
    }


    /**
     * Returns the redirect server addresses.
     * @return the redirect server addresses
     */
    public InetSocketAddress[] getRedirectServerAddresses () {
        return this.redirectServerAddresses;
    }


    /**
     * Periodically blocks until a request arrives, handles the latter subsequently.
     */
    public void run () {
        while (true) {
            Socket clientConnection = null;
            try {
                clientConnection = this.host.accept();
                this.threadPool.execute(new TcpSwitchConnectionHandler(this, clientConnection));
            } catch (final SocketException e) {
                break;
            } catch (final IOException e) {
                Logger.getGlobal().log(Level.WARNING, e.getMessage(), e);
            } catch (final RejectedExecutionException e) {
                try { clientConnection.close(); } catch (IOException ne) { e.addSuppressed(ne); }
                Logger.getGlobal().log(Level.WARNING, e.getMessage(), e);
            }
        }
    }


    /**
     * Returns the redirect server addresses loaded from a property file.
     * @return the redirect server addresses
     * @throws IOException if there is an I/O related problem
     */
    static protected InetSocketAddress[] redirectServerAddresses () throws IOException {
        try (InputStream byteSource = TcpSwitchServer.class.getResourceAsStream(PROPERTIES_FILE_NAME)) {
            final Map<String,String> properties = Maps.readProperties(byteSource);

            return properties
                    .values()
                    .stream()
                    .map(hostname -> InetAddresses.socketAddress(hostname))
                    .toArray(InetSocketAddress[]::new);
        }
    }


    /**
     * Application entry point. The given arguments are expected to be an optional service port (default is 8010),
     * and an optional session awareness (default false)
     * @param args the runtime arguments
     * @throws IllegalArgumentException if the given port is not a valid port number
     * @throws IOException if there is an I/O related problem
     */
    static public void main (final String[] args) throws IllegalArgumentException, NotDirectoryException, NoSuchFileException, AccessDeniedException, IOException {
        final int servicePort = args.length > 0 ? Integer.parseInt(args[0]) : 8010;
        final boolean sessionAware = args.length > 1 ? Boolean.parseBoolean(args[1]) : false;

        final InetSocketAddress serviceAddress = new InetSocketAddress(TcpServers.localAddress(), servicePort);
        final InetSocketAddress[] redirectAddresses = redirectServerAddresses();
        final TcpSwitchServer server = new TcpSwitchServer(servicePort, sessionAware, redirectAddresses);

        new Thread(server, "tcp-acceptor").start();
        try {
            System.out.format("TCP switch server running on %s:%s, enter \"quit\" to stop.\n", serviceAddress.getHostName(), serviceAddress.getPort());
            System.out.format("Configured redirect servers:\n");
            Arrays.stream(redirectAddresses).forEachOrdered(redirectAddress -> System.out.format("- %s\n", redirectAddress));
            System.out.format("Session awareness: %b\n", sessionAware);

            final BufferedReader charSource = new BufferedReader(new InputStreamReader(System.in));
            while (!"quit".equals(charSource.readLine()));
        } finally {
            server.close();
        }
    }
}
