# Chapter 2. Netty—asynchronous and event-driven

In this chapter, We will do 

- Writing an Echo server and client 
- Building and testing the applications

이번 챕터에서는 응답 서버와 클라이언트를 구현하고, 테스트를 작성해보겠다. 
Netty의 기본 설정은 생략하겠다. JDK  버전이 1.7, 1.8 이상이면 별 다른 설정은 건너뛰어도 된다. 1.6 이하에서도 가능하지만, 별도의 설정이 필요할 수 있다. 
JRE가 아닌 JDK를 설치해야함에 유의해라. JRE는 자바 어플리케이션의 실행만 가능하고 컴파일은 불가능하다. 

## Echo server

### Configuring a Toolset

All netty servers needs

- At least one `ChannelHandler`: This component implements the server's processing of data received from the client.—its business logic.
- `Bootstrapping`: A startup code configuring the server. It binds the server to the port on which it will listen for connection request. 

모든 네티 서버는

- 하나 이상의 `ChannelHandler`: 클라이언트로부터 받은 데이터를 처리하는 서버의 비즈니스 로직을 구현. 
- `Bootstrapping`: 서버의 시작 코드 설정. 이 과정에서 서버와 연결 요청을 수신할 위한 포트를 바인드한다. 

### ChannelHandler and Business Logic

We demonstrated that events can be dispatched using `Future` and `Callback`s. 
And the `ChannelHandler` defines how to handle and respond to these events.
In a Netty application, all business logic is defined in the implementation of core abstractions.

Echo server will respond to incoming messages, we need to implement interface `ChannelInboundHandler`, which defines the methods to process the inbound events.
As our requirements are humble, so it require only a fer of these methods. So it will be sufficient to subclass `ChannelInboundHandlerAdapter`, default implementation of `ChannelInboundHandler`. 

The following methods interest us:
- `channelRead()` — Called for each incoming message
- `channelReadComplete()` — Notifies the handler that the last call made to channelRead() was the last message in the current batch
- `exceptionCaught()` — Called if an exception is thrown during the read operation

이전에 `Future`와 `Callback`을 활용하여 이벤트를 전파한다고 말했고, `ChannelHandler`에서 해당 이벤트를 어떻게 처리하고 응답할지 정의한다.
네티 어플리케이션에서는, 모든 비즈니스 로직은 핵심 추상화의 구현체에서 정의된다.

에코 서버는 인바운드 메세지를 

```java
// Indicated that a ChannelHandler can be shared by multiple channels.
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
```

`ChannelInboundHandlerAdapter` has a straightforward API, and each of its methods can be overridden to hook into the event lifecycle at the appropriate point.
Because you need to handle all received data, you override `channelRead()`. 
In this server you simply echo the data to the remote peer.







