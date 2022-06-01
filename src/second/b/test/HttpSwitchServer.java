package second.b.test;

import com.sun.net.httpserver.HttpServer;
import edu.sb.ds.util.Copyright;
import edu.sb.ds.util.InetAddresses;
import edu.sb.ds.util.Maps;
import edu.sb.ds.util.TcpServers;
import second.b.main.HttpSwitchRequestHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Map;


/**
 * This class models HTTP switch servers, i.e. "spray" servers for all kinds of HTTP based
 * protocol connections. It redirects incoming client connections to it's given set of redirect
 * servers, either randomly selected, or determined by known session association.
 */

@Copyright(year=2014, holders="Sascha Baumeister")
public final class HttpSwitchServer {
    static private final String PROPERTIES_FILE_NAME = "redirect-servers.properties";

    /**
     * Prevents external instantiation.
     */
    private HttpSwitchServer() {}




    /**
     * Returns the redirect server addresses loaded from a property file.
     * @return the redirect server addresses
     * @throws IOException if there is an I/O related problem
     */
    static protected InetSocketAddress[] redirectServerAddresses () throws IOException {
        try (InputStream byteSource = HttpSwitchServer.class.getResourceAsStream(PROPERTIES_FILE_NAME)) {
            final Map<String,String> properties = Maps.readProperties(byteSource);
            return properties
                    .values()
                    .stream()
                    .map(hostname -> InetAddresses.socketAddress(hostname))
                    .toArray(InetSocketAddress[]::new);
        }
    }


    /**
     * Application entry point. The given arguments are expected to be an optional service port
     * (default is 8010), an optional session awareness (default is false), and an optional
     * key store file path (default is null).
     * @param args the runtime arguments
     * @throws IllegalArgumentException if the given port is not a valid port number
     * @throws NotDirectoryException if the given directory path is not a directory
     * @throws NoSuchFileException if the given key store file path is neither {@code null} nor representing a regular file
     * @throws AccessDeniedException if key store file access is denied, or if any of the certificates within the key store
     *         could not be loaded, if there is a key recovery problem (like incorrect passwords), or if there is a key
     *         management problem (like key expiration)
     * @throws IOException if there is an I/O related problem
     */
    static public void main (final String[] args) throws IllegalArgumentException, NotDirectoryException, NoSuchFileException, AccessDeniedException, IOException {
        final int servicePort = args.length > 0 ?Integer.parseInt(args[0]) : 8010;
        final boolean sessionAware = args.length > 1 ? Boolean.parseBoolean(args[1]) : false;
        final Path keyStorePath = args.length > 2 ? Paths.get(args[2]).toAbsolutePath() : null;
        final String keyRecoveryPassword = args.length > 3 ? args[3] : "changeit";
        final String keyManagementPassword = args.length > 4 ? args[4] : keyRecoveryPassword;

        final boolean transportLayerSecurity = keyStorePath != null;
        final InetSocketAddress serviceAddress = new InetSocketAddress(TcpServers.localAddress(), servicePort);
        final InetSocketAddress[] redirectServerAddresses = redirectServerAddresses();
        final HttpServer server = TcpServers.newHttpServer(serviceAddress, keyStorePath, keyRecoveryPassword, keyManagementPassword);
        server.createContext("/", new HttpSwitchRequestHandler(sessionAware, redirectServerAddresses));

        server.start();
        try {
            final String origin = String.format("%s://%s:%s/", transportLayerSecurity ? "https" : "http", serviceAddress.getHostName(), serviceAddress.getPort());
            System.out.format("Web redirect server running on origin %s, enter \"quit\" to stop.\n", origin);
            System.out.format("Redirect host addresses: %s.\n", Arrays.toString(redirectServerAddresses));
            final BufferedReader charSource = new BufferedReader(new InputStreamReader(System.in));
            while (!"quit".equals(charSource.readLine()));
        } finally {
            server.stop(0);
        }
    }
}