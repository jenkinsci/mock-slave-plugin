package org.jenkinci.plugins.mock_slave;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * Deliberately slows down am I/O channel by a measured amount.
 * All times are given in microseconds.
 */
final class Throttler {

    private static final long MIN_LOG_TIME = 2500000; // 2.5s
    private static final long SLEEP_GRACE = 100; // 0.1ms

    private final long delay;
    private final long latency;
    private final long overhead;
    private final PrintStream log;
    private boolean direction;
    private boolean started;
    private String runningWhy;
    private long runningTimeout;

    Throttler(long delay, long latency, long overhead, PrintStream log) {
        this.delay = delay;
        this.latency = latency;
        this.overhead = overhead;
        this.log = log;
        if (active()) {
            log.printf("throttling with delay=%dµs latency=%dµs overhead=%dµs%n", delay, latency, overhead);
        }
    }

    private boolean active() {
        return delay > 0 || latency > 0 || overhead > 0;
    }

    private void sleep(long timeout, String why) {
        if (timeout == 0) {
            return;
        }
        synchronized (this) {
            if (runningWhy != null && !why.equals(runningWhy)) {
                if (runningTimeout >= MIN_LOG_TIME || Math.random() < (double) runningTimeout / MIN_LOG_TIME) {
                    log.printf("slept for %dµs: %s%n", runningTimeout, runningWhy);
                }
                runningTimeout = 0;
            }
            runningWhy = why;
            runningTimeout += timeout;
            if (runningTimeout >= MIN_LOG_TIME) {
                log.printf("sleeping for %dµs: %s%n", new Object[] {runningTimeout, runningWhy});
                runningTimeout = 0;
            }
        }
        if (timeout < SLEEP_GRACE && Math.random() > (double) timeout / SLEEP_GRACE) {
            return;
        }
        try {
            Thread.sleep(timeout / 1000000, (int) (timeout % 1000000));
        } catch (InterruptedException x) {
            x.printStackTrace(log);
        }
    }

    private void checkStarted() {
        boolean doSleep = false;
        synchronized (this) {
            if (!started) {
                started = true;
                doSleep = true;
            }
        }
        if (doSleep) {
            sleep(delay, "initial delay");
        }
    }

    private void data(boolean outgoing) {
        boolean doSleep = false;
        synchronized (this) {
            if (direction != outgoing) {
                direction = outgoing;
                doSleep = true;
            }
        }
        if (doSleep) {
            sleep(latency, "latency switch");
        }
    }

    OutputStream wrap(OutputStream base) {
        if (!active()) {
            return base;
        }
        return new FilterOutputStream(base) {
            @Override public void write(int b) throws IOException {
                checkStarted();
                sleep(overhead, "outgoing overhead");
                super.write(b);
                data(true);
            }
        };
    }

    InputStream wrap(InputStream base) {
        if (!active()) {
            return base;
        }
        return new ConvenientFilterInputStream(base) {
            @Override public int read() throws IOException {
                checkStarted();
                sleep(overhead, "incoming overhead");
                int c = super.read();
                data(false);
                return c;
            }
        };
    }

    private static abstract class ConvenientFilterInputStream extends FilterInputStream {
        ConvenientFilterInputStream(InputStream in) {
            super(in);
        }
        @Override public int read(byte[] b, int off, int len) throws IOException {
            if (len > 0) {
                int c = read();
                if (c == -1) {
                    return -1;
                } else {
                    b[off] = (byte) c;
                    return 1;
                }
            } else {
                return 0;
            }
        }
    }

}
