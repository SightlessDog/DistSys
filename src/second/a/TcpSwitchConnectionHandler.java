package second.a;

import edu.sb.ds.sync.ExampleCheckedException;
import edu.sb.ds.sync.ExampleWorker;
import edu.sb.ds.util.Uninterruptibles;

import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Instances of this class handle TCP client connections accepted by a TCP switch server.
 */
public class TcpSwitchConnectionHandler implements Runnable {
    private final TcpSwitchServer parent;
    private final Socket clientConnection;
    private final int processors = Runtime.getRuntime().availableProcessors();

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

    /**
     * Handles the client connection by transporting all data to a new server connection, and
     * vice versa. Closes all connections upon completion.
     */
    public void run () {
        try (Socket clientConnection = this.clientConnection) {
            try (Socket serverConnection = this.newServerConnection()) {
                final Callable<Long> transporter1 = () ->
                        clientConnection.getInputStream().transferTo(serverConnection.getOutputStream());

                final Callable<Long> transporter2 = () ->
                        serverConnection.getInputStream().transferTo(clientConnection.getOutputStream());
                // Forking two workers
                final RunnableFuture<?>[] futures = {
                        new FutureTask<>(transporter1),
                        new FutureTask<>(transporter2)
                };

                new Thread(futures[0], "worker-thread-0").start();
                new Thread(futures[1], "worker-thread-1").start();

                // Associated Join
                try {
                    for (final Future<?> future : futures) {
                        try {
                            Uninterruptibles.get(future);
                        } catch (final ExecutionException exception) {
                            final Throwable cause = exception.getCause();
                            if (cause instanceof Error) throw (Error) cause;
                            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                            if (cause instanceof IOException) throw (IOException) cause;
                            throw new AssertionError(exception);
                        }
                    }
                } finally {
                    for (final Future<?> future : futures)
                        future.cancel(true);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Socket newServerConnection() throws IOException {
        final int index;
        if (!this.parent.getSessionAware()) {
            index = Math.abs(ThreadLocalRandom.current().nextInt(2));
        } else {
            index = Math.abs(new Random(this.clientConnection.getInetAddress().hashCode()).nextInt(2));
        }
        Logger.getGlobal().log(Level.INFO, String.format("Redirecting from %s to %s%n", this.clientConnection.getRemoteSocketAddress(), this.parent.redirectServerAddresses[index]));
        return new Socket(this.parent.redirectServerAddresses[index].getHostName(),
                this.parent.redirectServerAddresses[index].getPort());
    }
}
