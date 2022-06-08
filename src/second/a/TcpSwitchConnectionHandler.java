package second.a;

import edu.sb.ds.sync.ExampleCheckedException;
import edu.sb.ds.sync.ExampleWorker;

import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.*;

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

    private String getFile (int random) {
        try {
            OutputStream backToServer = clientConnection.getOutputStream();
            HttpRequest htmlRequest = HttpRequest.newBuilder().uri(
                new URI("http://localhost:"
                        + parent.redirectServerAddresses[random].getPort()
                        + "/external/HelloWorld.html")
            ).GET().build();
            HttpResponse<String> htmlResponse =  HttpClient.newHttpClient()
                    .send(htmlRequest, HttpResponse.BodyHandlers.ofString());
            return  htmlResponse.body();
            /*PrintWriter writer = new PrintWriter(backToServer, true);
            writer.println("HTTP/1.1 200 OK");
            writer.println("Content-Type: text/html");
            writer.println("Content-Length: " + htmlResponse.body().length());
            writer.println();
            writer.println(htmlResponse.body());
            writer.flush();
            writer.close();
            clientConnection.close();*/
        } catch (IOException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return "";
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

        // Switch Server -> client
        try (Socket clientConnection = this.clientConnection) {
            // Switch Server -> server
            try (Socket serverConnection = this.newServerConnection()) {
                System.out.println("[CONNECTED]");
                final PrintWriter out = new PrintWriter(serverConnection.getOutputStream(), true);
                final InputStream in = serverConnection.getInputStream();
                final OutputStream toServer = clientConnection.getOutputStream();

                final Callable<Long> getFile = () -> {
                    final long timestamp = System.currentTimeMillis();
                    System.out.format("Thread %s: starting work.\n", Thread.currentThread().getName());
                    out.println("GET /external/HelloWorld.html HTTP/1.1");
                    out.println("Host: " + serverConnection.getInetAddress() + ":" + serverConnection.getPort());
                    out.println("Connection: Close");
                    out.println("");
                    in.transferTo(toServer);
                    System.out.format("Thread %s: stoping work.\n", Thread.currentThread().getName());
                    return System.currentTimeMillis() - timestamp;
                };

                final Callable<Long> sendFile = () -> {
                    final long timestamp = System.currentTimeMillis();
                    PrintWriter writer = new PrintWriter(toServer, true);
                    writer.println("HTTP/1.1 200 OK");
                    writer.println("Content-Type: text/html");
                    writer.println("hello");
                    return System.currentTimeMillis() - timestamp;
                };

                System.out.format("Main-Thread: Executing workers in new threads!\n");
                final RunnableFuture<Long>[] futures = new RunnableFuture[2];
                for (int index = 0; index < 2; ++index) {
                    // futures[index] = threadPool.submit(worker);
                    if (index == 0 ) {
                        futures[index] = new FutureTask<>(getFile);
                    } else {
                        futures[index] = new FutureTask<>(sendFile);
                    }
                    new Thread(futures[index], "worker-thread-" + index).start();
                }

                System.out.format("Main-Thread: Waiting for child threads to finish!\n");
                try {
                    for (final Future<Long> future : futures) {
                        try {
                            final long result = future.get();
                            System.out.format("child thread ended after %.2fs\n", result * 0.001);
                        } catch (final ExecutionException exception) {
                            final Throwable cause = exception.getCause();	// manual precise rethrow for cause!
                            if (cause instanceof Error) throw (Error) cause;
                            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                            if (cause instanceof ExampleCheckedException) throw (ExampleCheckedException) cause;
                            throw new AssertionError();
                        }
                    }
                } finally {
                    for (final Future<Long> future : futures)
                        future.cancel(true);
                }

                System.out.format("Main-Thread: All child threads are done!\n");

                // 2 threads (futures) client and server, for the InputStream.transferTo()
              // Using futures, example: ResyncThreadByFutureInterruptibly
            }
            // new Socket(host,port) muss in newServerConnection ausgerufen werden
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Socket newServerConnection() throws IOException {
        // comes under the method newServerConnection
        final int index;
        if (!parent.getSessionAware()) {
            // TODO recursion oder Schleif
            index = Math.abs(ThreadLocalRandom.current().nextInt() % 2);
                /*final URL url = new URL("http://:" + parent.redirectServerAddresses[random].getHostName() +
                        ":" + parent.redirectServerAddresses[random].getPort());
                final HttpURLConnection huc = (HttpURLConnection) url.openConnection();

                if (huc.getResponseCode() != HttpURLConnection.HTTP_OK ) {
                    random = (random + 1) % 2;
                }*/
                //getFile(random);
        } else {
            index = Math.abs(new Random(clientConnection.getInetAddress().hashCode()).nextInt() % 2);
            //getFile(random);
        }
        return new Socket(parent.redirectServerAddresses[index].getHostName(),
                parent.redirectServerAddresses[index].getPort());
    }
}
