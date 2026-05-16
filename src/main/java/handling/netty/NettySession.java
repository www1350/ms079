package handling.netty;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.apache.mina.core.filterchain.DefaultIoFilterChain;
import org.apache.mina.core.filterchain.IoFilterChain;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.session.DummySession;
import org.apache.mina.core.write.WriteRequest;

import java.net.SocketAddress;

/**
 * MINA IoSession wrapper around a Netty {@link Channel}.
 * Allows existing code that uses {@code session.write()} / {@code session.close()} to work
 * transparently with Netty connections.
 *
 * Attribute methods (getAttribute/setAttribute/removeAttribute) are final in
 * AbstractIoSession and use MINA's internal IoSessionAttributeMap.
 *
 * {@code close()} is final in AbstractIoSession, so we hook into the close chain
 * by overriding {@code getFilterChain()} with a custom {@link DefaultIoFilterChain}
 * whose {@code fireFilterClose()} also closes the Netty channel.
 */
public final class NettySession extends DummySession {

    private final Channel channel;
    private final IoFilterChain filterChain;

    public NettySession(Channel channel) {
        this.channel = channel;
        this.filterChain = new DefaultIoFilterChain(this) {
            @Override
            public void fireFilterClose() {
                channel.close();
                super.fireFilterClose();
            }
        };
    }

    public Channel getNettyChannel() {
        return channel;
    }

    // --- filter chain: hook into the final close() method ---

    @Override
    public IoFilterChain getFilterChain() {
        return filterChain;
    }

    // --- write: delegate to Netty Channel pipeline ---

    @Override
    public WriteFuture write(Object message) {
        channel.writeAndFlush(message);
        return null;
    }

    @Override
    public WriteFuture write(Object message, SocketAddress remoteAddress) {
        channel.writeAndFlush(message);
        return null;
    }

    // --- addresses ---

    @Override
    public SocketAddress getRemoteAddress() {
        return channel.remoteAddress();
    }

    @Override
    public SocketAddress getLocalAddress() {
        return channel.localAddress();
    }

    @Override
    public SocketAddress getServiceAddress() {
        return channel.localAddress();
    }

    // --- Netty Channel attribute helpers ---

    @SuppressWarnings("unchecked")
    public <T> T getNettyAttr(AttributeKey<T> key) {
        return channel.attr(key).get();
    }

    public <T> void setNettyAttr(AttributeKey<T> key, T value) {
        channel.attr(key).set(value);
    }
}
