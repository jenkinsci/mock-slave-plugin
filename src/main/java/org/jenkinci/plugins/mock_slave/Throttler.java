package org.jenkinci.plugins.mock_slave;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Deliberately slows down am I/O channel by a measured amount.
 */
final class Throttler {

    /** ms before written data becomes available */
    private final int latency;
    /** bps that can be transferred */
    private final int bandwidth;
    private final InputStream is;
    private final OutputStream os;

    @SuppressWarnings("CallToThreadStartDuringObjectConstruction")
    Throttler(int latency, int bandwidth, InputStream is, OutputStream os) throws IOException {
        this.latency = latency;
        this.bandwidth = bandwidth;
        UnboundedBlockingByteQueue in = new UnboundedBlockingByteQueue("in", 128 * 1024, 1.3f);
        new StreamCopyThread("incoming", is, new DelayedOutputStream(in)).start();
        this.is = new DelayedInputStream(in);
        UnboundedBlockingByteQueue out = new UnboundedBlockingByteQueue("out", 128 * 1024, 1.3f);
        new StreamCopyThread("outgoing", new DelayedInputStream(out), os).start();
        this.os = new DelayedOutputStream(out);
    }

    InputStream is() {
        return is;
    }

    OutputStream os() {
        return os;
    }

    private static class DelayedInputStream extends InputStream {

        private final UnboundedBlockingByteQueue stream;

        DelayedInputStream(UnboundedBlockingByteQueue stream) {
            this.stream = stream;
        }

        @SuppressWarnings({"SleepWhileInLoop", "PointlessBitwiseExpression"})
        @Override public int read() throws IOException {
            try {
                long t = ((long) stream.read() << 56) +
                         ((long) (stream.read() & 255) << 48) +
                         ((long) (stream.read() & 255) << 40) +
                         ((long) (stream.read() & 255) << 32) +
                         ((long) (stream.read() & 255) << 24) +
                         ((stream.read() & 255) << 16) +
                         ((stream.read() & 255) <<  8) +
                         ((stream.read() & 255) <<  0);
                if (t == 0) { // EOF
                    //stream.log("got EOF");
                    return -1;
                }
                //stream.log("read time " + new Date(t));
                long now;
                while ((now = System.currentTimeMillis()) < t) {
                    //stream.log("sleeping for " + (t - now) + "msec");
                    Thread.sleep(t - now);
                }
                byte b = stream.read();
                //stream.log("read " + b);
                return ((int) b + 256) % 256;
            } catch (InterruptedException x) {
                throw new IOException(x);
            }
        }

        @Override public int read(byte[] b, int off, int len) throws IOException {
            int max = Math.min(len, Math.max(available(), 1));
            //stream.log("requested " + len + " and will try to read " + max);
            int i = 0;
            for (; i < max; i++) {
                int c = read();
                if (c == -1) {
                    //stream.log("read to EOF " + i + "/" + len);
                    return i - 1;
                } else {
                    b[off + i] = (byte) c;
                }
            }
            //stream.log("read " + i + "/" + len);
            return i;
        }

        @Override public int available() throws IOException {
            int r = stream.available() / 9;
            //stream.log(r + " bytes available");
            return r;
        }

    }

    private class DelayedOutputStream extends OutputStream {

        private final UnboundedBlockingByteQueue stream;

        DelayedOutputStream(UnboundedBlockingByteQueue stream) {
            this.stream = stream;
        }

        @SuppressWarnings("PointlessBitwiseExpression")
        @Override public void write(int b) throws IOException {
            // XXX take bandwidth into account: possibly make t bigger if we have already written too much
            long t = System.currentTimeMillis() + latency;
            //stream.log("writing time " + new Date(t));
            stream.write((byte) (t >>> 56));
            stream.write((byte) (t >>> 48));
            stream.write((byte) (t >>> 40));
            stream.write((byte) (t >>> 32));
            stream.write((byte) (t >>> 24));
            stream.write((byte) (t >>> 16));
            stream.write((byte) (t >>>  8));
            stream.write((byte) (t >>>  0));
            //stream.log("wrote " + b);
            stream.write((byte) b);
        }

        @Override public void close() throws IOException {
            //stream.log("closing");
            for (int i = 0; i < 8; i++) {
                stream.write((byte) 0);
            }
        }

    }

    private static class StreamCopyThread extends Thread {

        private final InputStream in;
        private final OutputStream out;

        StreamCopyThread(String threadName, InputStream in, OutputStream out) {
            super(threadName);
            this.in = in;
            this.out = out;
        }

        @Override public void run() {
            try {
                try {
                    int c;
                    while ((c = in.read()) != -1) {
                        out.write(c);
                    }
                    //System.err.println("eof on " + getName());
                } finally {
                    in.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

}
