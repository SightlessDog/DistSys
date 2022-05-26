package vt.second;

import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Instances of this class handle TCP client connections accepted by a TCP switch server.
 */
public class TcpSwitchConnectionHandler implements Runnable {
    private final TcpSwitchServer parent;
    private final Socket clientConnection;

    /**
     * Initializes a new instance from a given client connection.
     * @parent the parent switch
     * @param clientConnection the connection
     * @throws NullPointerException if any of the given arguments is {@code null}
     */
    public TcpSwitchConnectionHandler (final TcpSwitchServer parent, final Socket clientConnection) {
        if (parent == null | clientConnection == null) throw new NullPointerException();

        this.parent = parent;
        this.clientConnection = clientConnection;
    }

    private void getFile (int random) {
        try {
            OutputStream backToServer = clientConnection.getOutputStream();
            HttpRequest htmlRequest = HttpRequest.newBuilder().uri(
                new URI("http://localhost:"
                        + parent.redirectServerAddresses[random].getPort()
                        + "/external/HelloWorld.html")
            ).GET().build();
            HttpResponse<String> htmlResponse =  HttpClient.newHttpClient()
                    .send(htmlRequest, HttpResponse.BodyHandlers.ofString());
            PrintWriter writer = new PrintWriter(backToServer, true);
            writer.println("HTTP/1.1 200 OK");
            writer.println("Content-Type: text/html");
            writer.println("Content-Length: " + htmlResponse.body().length());
            writer.println();
            writer.println(htmlResponse.body());
            writer.flush();
            writer.close();
            clientConnection.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    /**
     * Handles the client connection by transporting all data to a new server connection, and
     * vice versa. Closes all connections upon completion.
     */
    public void run () {
        // TODO implement TCP switching here, and close the connections upon completion!
        // Note that you'll need 1-2 new transporter threads to complete this operation,
        // as you cannot foresee if the client or the server closes the connection, or if
        // the protocol communicated involves handshakes. Either case implies you'd
        // end up reading "too much" if you try to transport both communication directions
        // within this thread, creating a deadlock scenario!
        //   Especially make sure that all connections are properly closed in
        // any circumstances! Note that closing one socket stream closes the underlying
        // socket connection as well. Also note that a SocketInputStream's read() method
        // will throw a SocketException when interrupted while blocking, which is "normal"
        // behavior and should be handled as if the read() Method returned -1!
        //   Hint for sessionAware mode: The simplest solution is using a scrambler, i.e.
        // a randomizer that uses the client address as a seed, as this will repeatedly
        // create the same pseudo-random number (serverIndex) as next value. If you can't
        // realize this solution, using a cache in the form of clientAddress->serverSocketAddress
        // mappings is probably the next best alternative ...
        if (!parent.getSessionAware()) {
            int random = Math.abs(ThreadLocalRandom.current().nextInt() % 2);
            try {
                URL url = new URL("http://localhost:" + parent.redirectServerAddresses[random].getPort());
                HttpURLConnection huc = (HttpURLConnection) url.openConnection();
                if (huc.getResponseCode() != HttpURLConnection.HTTP_OK ) {
                    random = (random + 1) % 2;
                }
                getFile(random);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            int serverIndex = Math.abs(new Random(clientConnection.getInetAddress().hashCode()).nextInt() % 2);
            getFile(serverIndex);
        }
    }
}
