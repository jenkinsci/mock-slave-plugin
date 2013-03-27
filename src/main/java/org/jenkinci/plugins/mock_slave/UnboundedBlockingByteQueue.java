package org.jenkinci.plugins.mock_slave;

/**
 * Why does this not exist anywhere else?!
 * (Or does it?)
 * Optimized for simplicity, not performance.
 */
final class UnboundedBlockingByteQueue {

    private final String id;
    private final float growth;
    /** expandable circular buffer */
    private byte[] buf;
    /** earliest/read position */
    private int start;
    /** bytes currently in queue */
    private int size;

    UnboundedBlockingByteQueue(String id, int initialCapacity, float growth) {
        this.id = id;
        if (initialCapacity < 1 || growth <= 1) {
            throw new IllegalArgumentException();
        }
        buf = new byte[initialCapacity];
        this.growth = growth;
    }

    private boolean invariants() {
        return start >= 0 && start < buf.length && size >= 0 && size <= buf.length;
    }

    synchronized void write(byte b) {
        assert invariants();
        //log("writing with size " + size);
        if (size == buf.length) {
            byte[] buf2 = new byte[Math.round(size * growth + 1)];
            assert buf2.length > buf.length;
            log("expanding from " + size + " to " + buf2.length);
            System.arraycopy(buf, start, buf2, 0, size - start);
            System.arraycopy(buf, 0, buf2, size - start, start);
            buf = buf2;
            start = 0;
        } else if (size == 0) {
            notifyAll();
        }
        buf[(start + size) % buf.length] = b;
        size++;
        assert invariants();
    }

    synchronized byte read() throws InterruptedException {
        assert invariants();
        while (size == 0) {
            //log("waiting to read");
            wait();
            assert invariants();
        }
        byte b = buf[start];
        start = (start + 1) % buf.length;
        size--;
        assert invariants();
        return b;
    }

    synchronized int available() {
        assert invariants();
        return size;
    }

    void log(String msg) {
        System.err.println("@" + id + " " + msg);
    }

}
