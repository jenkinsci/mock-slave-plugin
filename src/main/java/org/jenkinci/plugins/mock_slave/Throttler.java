package org.jenkinci.plugins.mock_slave;

import hudson.util.StreamCopyThread;
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
        UnboundedBlockingByteQueue in = new UnboundedBlockingByteQueue(128 * 1024, 1.3f);
        new StreamCopyThread("incoming", is, new DelayedOutputStream(in)).start();
        this.is = new DelayedInputStream(in);
        /* XXX should be:
        UnboundedBlockingByteQueue out = new UnboundedBlockingByteQueue(128 * 1024, 1.3f);
        new StreamCopyThread("outgoing", new DelayedInputStream(out), os).start();
        this.os = new DelayedOutputStream(out);
        * but this causes a hang after a while (blocked reading empty queue) for unknown reasons:
	at org.jenkinci.plugins.mock_slave.UnboundedBlockingByteQueue.read(UnboundedBlockingByteQueue.java:58)
	at org.jenkinci.plugins.mock_slave.Throttler$DelayedInputStream.read(Throttler.java:73)
	at java.io.ObjectInputStream$PeekInputStream.peek(ObjectInputStream.java:2272)
	at java.io.ObjectInputStream$BlockDataInputStream.peek(ObjectInputStream.java:2565)
	at java.io.ObjectInputStream$BlockDataInputStream.peekByte(ObjectInputStream.java:2575)
	at java.io.ObjectInputStream.readObject0(ObjectInputStream.java:1315)
	at java.io.ObjectInputStream.readObject(ObjectInputStream.java:369)
	at hudson.remoting.Command.readFrom(Command.java:92)
	at hudson.remoting.ClassicCommandTransport.read(ClassicCommandTransport.java:59)
	at hudson.remoting.SynchronousCommandTransport$ReaderThread.run(SynchronousCommandTransport.java:48)
        * so instead for now we are just making outgoing channel a direct link:
        */
        this.os = os;
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
                    stream.log("got EOF");
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
            if (len == 0) {
                return 0;
            } else {
                int c = read();
                if (c == -1) {
                    return -1;
                } else {
                    b[off] = (byte) c;
                    return 1;
                }
            }
        }

        /* XXX not correct, should be 0 at EOF and >=1 otherwise, but how to determine this easily w/o blocking?
        @Override public int available() throws IOException {
            return 1;
        }
        */

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
            stream.log("closing");
            for (int i = 0; i < 8; i++) {
                stream.write((byte) 0);
            }
        }

    }

}
