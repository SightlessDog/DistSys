package C;

import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.*;

import edu.sb.ds.sort.MergeSorter;
import edu.sb.ds.sort.SingleThreadSorter;
import edu.sb.ds.util.Copyright;
import edu.sb.ds.util.Uninterruptibles;

/**
 * Multi-threaded merge sorter implementation that distributes elements evenly over two child
 * sorters, sorts them separately using two separate threads, and then merges the two sorted
 * children's elements during read requests. Note that this implementation is able to scale its
 * workload over two processor cores, and even more if such sorters are stacked. However, all
 * elements are still stored within the RAM of a single process.
 * @param <E> the element type to be sorted in naturally ascending order
 */
@Copyright(year=2010, holders="Sascha Baumeister")
public class MultiThreadSorter<E extends Comparable<E>> implements MergeSorter<E> {

    private final Comparator<E> comparator = Comparator.nullsLast(Comparator.naturalOrder());
    private final MergeSorter<E> leftChild, rightChild;
    private E leftReadCache, rightReadCache;
    private boolean leftWrite;
    private State state;

    static final ExecutorService THREAD_POOL = Executors.newFixedThreadPool(20);


    /**
     * Initializes a new instance in {@link State#WRITE} state that is based on two child sorters.
     * @param leftChild the left child sorter
     * @param rightChild the right child sorter
     * @throws NullPointerException if any of the given children is {@code null}
     */

    public MultiThreadSorter(final MergeSorter<E> leftChild, final MergeSorter<E> rightChild) {
        if (leftChild == null || rightChild == null) throw new NullPointerException();

        this.leftChild = Objects.requireNonNull(leftChild);
        this.rightChild = Objects.requireNonNull(rightChild);
        this.leftWrite = true;
        this.state = State.WRITE;
    }


    /**
     * {@inheritDoc}
     */
    public void close () throws IOException {
        try {
            try {
                this.leftChild.close();
            } finally {
                this.rightChild.close();
            }
        } finally {
            this.state = State.CLOSED;
        }
    }


    /**
     * {@inheritDoc}
     */
    public void write (final E element) throws IllegalStateException, IOException {
        if (this.state != State.WRITE) throw new IllegalStateException(this.state.name());

        if (element == null) {
            this.leftChild.write(null);
            this.rightChild.write(null);
            this.leftWrite = true;
            this.state = State.SORT;
        } else {
            (this.leftWrite ? this.leftChild : this.rightChild).write(element);
            this.leftWrite = !this.leftWrite;
        }
    }


    /**
     * {@inheritDoc}
     */
    public void sort() throws IllegalStateException {
        if (this.getState() != State.SORT) throw new IllegalStateException(this.state.name());
        final Callable<?> leftChildThread = () -> {
            this.leftChild.sort();
            this.leftReadCache = this.leftChild.read();

            return "done";
        };
        final Callable<?> rightChildThread = () -> {
            this.rightChild.sort();
            this.rightReadCache = this.rightChild.read();
            return "done";
        };

        final Future<?>[] futures = {
            THREAD_POOL.submit(leftChildThread),
            THREAD_POOL.submit(rightChildThread)
        };

        try {
            for (final Future<?> future : futures) {
                try {
                    var result = Uninterruptibles.get(future);
                } catch (final ExecutionException exception) {
                    final Throwable cause = exception.getCause();	// manual precise rethrow for cause!
                    if (cause instanceof Error) throw (Error) cause;
                    if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                    throw new AssertionError();
                }
            }
        } finally {
            for (final Future<?> future : futures)
                future.cancel(true);
        }
        this.state = State.READ;
    }


    /**
     * {@inheritDoc}
     */
    public E read () throws IllegalStateException, IOException {
        if (this.getState() != State.READ) throw new IllegalStateException(this.state.name());

        final E result;
        if (this.leftReadCache == null & this.rightReadCache == null) {
            result = null;
            this.state = State.WRITE;
        } else if (this.comparator.compare(this.leftReadCache, this.rightReadCache) <= 0) {
            result = this.leftReadCache;
            this.leftReadCache = this.leftChild.read();
        } else {
            result = this.rightReadCache;
            this.rightReadCache = this.rightChild.read();
        }

        return result;
    }


    /**
     * {@inheritDoc}
     */
    public State getState () {
        return this.state;
    }


    /**
     * Returns the root sorter instance of a balanced recursion tree of new sorters.
     * The tree will contain as many single-thread sorter instances as there are processors
     * within this system. If there is exactly one processor within this system, the result
     * will be the sole single-thread sorter instance created. Otherwise, the result will
     * be a multi-thread sorter instance.
     *
     * @return the root sorter created
     */
    static public <T extends Comparable<T>> MergeSorter<T> newInstance () {
        Queue<MergeSorter> sorterQ = new LinkedList<>();

        for (int i=0; i<Runtime.getRuntime().availableProcessors(); i++) {
            sorterQ.add(SingleThreadSorter.newInstance());
        }

        while (sorterQ.size() > 1) {
            final MergeSorter leftChild = sorterQ.remove();
            final MergeSorter rightChild = sorterQ.remove();
            sorterQ.add(new MultiThreadSorter(leftChild, rightChild));
        }
        // Create a queue containing as many single-thread sorter instances as there are
        // processors within this system - which will be at least one.
        // While there is more than one sorter within said queue, remove two of them,
        // use these to create a new multi-thread sorter instance, and add the latter to the queue -
        // make sure this follows first in first out semantics.
        // This way, the queue is guaranteed to contain exactly one element in the end, which shall be returned.
        return sorterQ.remove();
    }
}