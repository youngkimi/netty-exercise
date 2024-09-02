import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.net.InetSocketAddress;

public class EchoServer {
    private final int port;

    public EchoServer(int port) {
        this.port = port;
    }

    public static void main(String[] args) throws Exception {

        if (args.length != 1) {
            System.out.println(
                "Usage: " + EchoServer.class.getSimpleName() + " <port>");
        }

        int port = Integer.parseInt(args[0]);
        new EchoServer(port).start();
    }

    public void start() throws Exception {
        final EchoServerHandler serverHandler = new EchoServerHandler();
        // 1. Create a EventLoop
        EventLoopGroup group = new NioEventLoopGroup();

        try {
            // 2. Create a bootstrap
            ServerBootstrap b = new ServerBootstrap();
            b.group(group)
                // 3. Specifies the use of an NIO transport Channel
                .channel(NioServerSocketChannel.class)
                // 4. Sets the socket address using the specified port
                .localAddress(new InetSocketAddress(port))
                // 5. Adds an EchoServerHandler to the Channel's ChannelPipeline.
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel)
                        throws Exception {
                            socketChannel.pipeline().addLast(serverHandler);
                    }
                });
            // 6. Binds the server asynchronously; sync() waits for the bind to complete.
            ChannelFuture f = b.bind().sync();
            // 7. Gets the CloseFuture of the Channel and blocks the current thread until it's complete
            f.channel().closeFuture().sync();
        } finally {
            // 8. Shuts down the EventLoop Group, releasing all resources.
            group.shutdownGracefully().sync();
        }
    }
}
