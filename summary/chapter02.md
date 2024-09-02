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

우리는 단순한 기능의 `EchoServer`를 만들려하므로, `ChannelInboundHandler`의 기본 구현체인 `ChannelInboundHandlerAdapter`을 사용하면 충분하다.
해당 인터페이스에서 우리가 관심 있는 메서드는 인바운드 메세지를 수신하는 `channelRead()`, 현재 메세지가 메세지 배치의 끝임을 알리는 `channelReadComplete()`, 예외 발생을 알리는 `exceptionCaught()`이다.

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

`ChannelInbouncHandlerAdapter` has a  API, and each of its methods can be overridden to hook into the event lifecycle at the appropriate point.
You need to override `channelRead()` to handle all received data. In this server, our goal is to simply echo the data to the remote peer.

By overriding `exceptionCaught()`, you can log the exception and define how to react to it. 
You can close the connection, or if you prefer, try to reconnect. In this case, we will simply close the connection when an error occurs.

> #### WHAT HAPPENS IF AN EXCEPTION ISN’T CAUGHT?
> At least one `ChannelHandler` in your application needs to handle exceptions.
> Every Channel has an associated ChannelPipeline, which holds a chain of `ChannelHandler` instances.
> If an exception occurs and is not caught in `exceptionCaught()`, it will be passed through the pipeline to the end of the `ChannelHandler` chain.
> The exception will be logged, but might not be handled properly.

- `ChannelHandler`s are invoked for different types of events.
- Applications implement or extend `ChannelHandler`s to hook into the event lifecycle and provide custom application logic.
- Architecturally, `ChannelHandler`s help to keep your business logic decoupled from networking code. This simplifies development as the code evolves in response to changing requirements.

### Bootstrapping the Server 

Having covered the core business logic provided by `EchoServerHandler`, we can look at how the server is set up:
1. Binding the server to a specific port to listen for and accept incoming connection requests
2. Setting up the server so that incoming messages are handled by `EchoServerHandler`

```java
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
```

In step `2`, you create a `ServerBootstrap` instance. 
Since you're using NIO for networking, you set up `NioEventLoopGroup` at `1` to accept and handle new connections and choose `NioServerSocketChannel` as the type of channel at `3`. 
Then, you specify the local address with the chosen port at `4`.
The server will use this address to listen for incoming connection requests.

In step 5, a special class called ChannelInitializer is used, which is crucial. 
When a new connection is accepted, a new child `Channel` is created, and `ChannelInitializer` adds an instance of `EchoServerHandler` to the `ChannelPipeline` of that `Channel`. 
This setup allows the `EchoServerHandler` to receive notifications about inbound messages.

이렇게 이 핸들러는 인바운드 메시지에 대한 알림을 받을 수 있다.

While `NIO` offers good scalability, configuring multi-threading can be challenging.
Netty's design encapsulates much of this complexity, and we will cover the relevant abstractions in Chapter 3.

Then, bind the server and be blocked for the binding to complete (the sync() call blocks until the bind operation is finished).
In step 7, wait until the server's channel is closed (similarly, sync() is called on the CloseFuture of the Channel). 
Finally, shut down the `EventLoopGroup` to release all resources, including any created threads.

`2`에서 `ServerBootstrap` 인스턴스를 생성한다.
`NIO`를 사용할 것이므로, `1`에서 새로운 연결을 수용하고 처리할 이벤트 그룹으로 `NioEventLoopGroup`을 생성, 부트스트랩의 채널 타입으로는 `3`에서 `NioServerSocketChannel`을 설정한다.
이후에 `4`에서는 선택한 포트로 설정된 `InetSocketAddress`을 로컬 주소로 설정한다. 서버는 이 주소로 바인드하여 새로운 연결 요청을 수신할 것이다.

`5`에서 `ChannelInitializer`라는 특별한 클래스를 사용한다. 이게 핵심이다.
새로운 연결이 수락되면 새로운 자식 `Channel`이 생성되고, `ChannelInitializer`가 `EchoServerHandler`의 인스턴스를 그 `Channel`의 `ChannelPipeline`에 추가한다.

`NIO`는 확장성이 좋지만, 멀티스레딩과 관련한 설정은 쉽지 않다.
네티의 디자인은 많은 복잡성을 캡슐화하고 우리는 관련 추상화를 챕터 3에서 다룰 것이다.

그리고 서버를 바인드하고, 바인딩 완료를 기다린다. (`sync()`로 바인드 완료까지 block 된다.)
`7`에서 서버의 채널이 닫힐 때 까지 기다린다. (마찬가지로 `Channel`의 `CloseFuture`를 `sync()` 했으므로)
그리고 `EventLoopGroup`을 종료하여 생성된 모든 스레드를 포함한 자원을 해제한다. 

In this example, we use `NIO`, the most commonly used communication method today, due to its scalability and thorough asynchronous processing. 
However, other methods like `OIO` can also be used. 
If you choose to use `OIO`, you would use `OioServerSocketChannel` and `OioEventLoopGroup`. We will explore this in Chapter 4.

이 예시에서는 현재 확장성과 철저한 비동기처리 덕분에 현재 가장 많이 사용되는 통신 방식인 `NIO`를 사용한다. 
하지만 `OIO`와 같은 다른 방식 또한 사용할 수 있다. 만약 사용하려면 `OioServerSocketChannel`, `OioEventLoopGroup`를 사용하면 된다.
이는 챕터 4에서 살펴볼 것이다. 

> ### Sum up
> #### Primary Code Component 
> - The `EchoServerHandler` implements the business logic.
> - The `main()` method bootstraps the server.
> #### What is required in bootstrapping
> - Create a `ServerBootstrap` instance to bootstrap and bind the server.
> - Create and assign an `NioEventLoopGroup` instance to handle event processing, such as accepting new connections and reading/writing data.
> - Specify the local `InetSocketAddress` to which the server binds.
> - Initialize each new `Channel` with an `EchoServerHandler` instance

요약하자면, `비즈니스 로직과 서버 생성 로직을 분리`하는 것이 우리의 목표라고 할 수 있겠다. 
- 비즈니스 로직은 `ChannelHandler`에서 처리.
- 서버는 부트스트랩으로 원하는 통신 방식(`NIO`, `OIO`)에 따라 이벤트 그룹과 소켓, 채널의 타입을 맞춰 설정하여 생성..
- 이후 서버에 비즈니스 로직을 주입. 새로운 연결 요청 시에 새로운 채널을 생성하는데, 그 때 맞는 `ChannelHandler`를 주입하여 비즈니스 로직을 수행할 수 있도록 한다. 

이제 클라이언트를 만들자. 

## Echo Client

### Requirements
1. Connect to the server.
2. Send one or more messages.
3. For each message, wait for and receive the same message back from the server.
4. Close the connection. 

### Implementing the client logic with `ChannelHandlers`

Like the server, the client involves the same two main code areas; business logic and bootstrapping.

The client will have a `ChannelInboundHandler` to process the data like the server. 
In this case, `SimpleChannelInboundHandler` is enough to fulfill our requirements. 

- `channelActive()` - called after the connection to the server is established.
- `channelRead0()` - called when a message is received from the server.
- `exceptionCaught()` - called if an exception is raised during processing.

```java
@Sharable
public class EchoClientHandler extends SimpleChannelInboundHandler<ByteBuf> {

	// notify that channel is active by sending a message.
	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		ctx.writeAndFlush(Unpooled.copiedBuffer("Netty rocks!", CharsetUtil.UTF_8));
	}

	@Override
	protected void channelRead0(ChannelHandlerContext channelHandlerContext, ByteBuf in) throws Exception {
		System.out.println(
				"Client received: " + in.toString(CharsetUtil.UTF_8));
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		cause.printStackTrace();
		ctx.close();
	}
}
```

`channelActive()` will be invoked when the connection to server has been established.
This ensures we can send a message to the server as soon as possible, which, in this case, is a byte buffer encoded with the string "Netty rocks!".

`channelRead0()`, where you can process the received data, will be invoked when data is received from the server. 
Note that the message will be received in chunks, so there is no guarantee that all the bytes will be received at once. 
Even for such a small amount of data, `channelRead0()` might be called twice: 
the first time with a `ByteBuf` (Netty's byte container) holding 3 bytes, and the second time with a `ByteBuf` holding 2 bytes. 
TCP guarantees that the `ByteBuf`s will arrive in the order in which they were sent by the server.

`exceptionCaught()` is same as the one in `EchoServerHandler`.

### Bootstrapping the Client 



<!-- 
    Blocking 보다는 Non-Blocking의 Context Switch 비용이 더 크다.
    Blocking은 외부 API에 보낸 요청의 응답을 대기하는 스레드가 유휴 상태로 진입하면서 해당 Context를 저장하고, 이어 작업을 재개할 때 Context를 보존해야 하기 때문이다. 
    Non-Blocking은 응답을 대기하면서 스레드가 유휴 상태로 진입하지 않고, 다른 작업을 진행할 수 있다. 이 다른 작업은, Netty의 이벤트 큐의 적재되어 있다. 
    이러한 이유로 기본적으로 Blocking 방식의 Tomcat의 기본 스레드 설정 개수는 200개로 Non-Blocking 방식의 Netty는 기기 코어 * 2 방식의 스레드 수보다 훨씬 많다. 
    스레드 수가 많다는 것은, 더 많은 Context Switch 비용이 발생한다는 것을 의미하고, 스레드 간 경쟁상태로 돌입할 여지가 더 많음을 의미한다. 
    (물론 Tomcat 또한 Java NIO를 활용하여 Non Blocking 방식으로 동작하게끔 할 수 있기는 하다.)

    비동기 방식으로 작동한다면 만약 응답이 도착 했을 때, 요청을 보낸 스레드가 작업을 이어 진행해야 하지 않을까?
    그렇지 않다면, 응답을 받은 스레드는 기존 작업 내용을 모르기 때문에 요청을 보낸 스레드의 상태를 적재하는 과정이 필요하고, 이는 곧 Context Switch와 비슷한 영향을 낼 것이다. 
    책에 내용이 있을 법 한데 봐야겠다...
-->

