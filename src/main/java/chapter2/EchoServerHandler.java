package chapter2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.CharsetUtil;

// Indicated that a ChannelHandler can be shared by multiple channels. (For documentation)
// It means this Handler is thread-safe. If this annotation is not specified, you have to create a new handler instance every time.
@Sharable
public class EchoServerHandler extends ChannelInboundHandlerAdapter {

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) {
		ByteBuf in = (ByteBuf) msg;
		// Logs the message to the console.
		System.out.println(
			"Server received: " + in.toString(CharsetUtil.UTF_8));
		// Writes the received message to the sender without flushing outbound message.
		ctx.write(in);
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) {
		// Flushes pending messages to this remote peer and closes the channel.
		ctx.writeAndFlush(Unpooled.EMPTY_BUFFER)
			.addListener(ChannelFutureListener.CLOSE);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		// Prints the exception stack trace.
		// Close the channel.
		cause.printStackTrace();
		ctx.close();
	}
}
