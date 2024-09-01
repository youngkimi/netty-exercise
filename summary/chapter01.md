# Chapter 1. Netty—asynchronous and event-driven

## Traditional Socket Network Programming for Java API(java.net)

```java
public class BlockingNetworking implements Runnable {

    int portNumber = 0;

    public BlockingNetworking(int portNumber) {
        this.portNumber = portNumber;
    }


    private static String processRequest(String request) {
        return "Get Request : " + request;
    }

    @Override
    public void run() {

        /*
            Use try-with-resources for I/O operations that implement AutoCloseable.
            It prevents resource leaks and keeps the code concise.
         */

        try (ServerSocket serverSocket = new ServerSocket(portNumber)) {
            /*
                `accept()` blocks until a connection is established on server socket.
                Then return a new socket for communication between server and client.
             */

            Socket clientSocket = serverSocket.accept();

            /*
                `BufferedReader` read texts from a character input stream.
                `PrintWriter` prints the result of process for request to output stream.
             */

            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            String request, response;

            /*
                The processing loop continues until the client send `Done`.
             */

            while ((request = in.readLine()) != null) {
                if ("Done".equals(request)) {
                    break;
                }
                response = processRequest(request);
                out.println();
            }
        } catch (IOException e) {
            System.out.println("Exception Occurs: " + e.getMessage());
        }
    }

}
```

## Problems due to blocking I/O

1. At any point, lots of thread could be dormant, waiting for unpredictable input/output data to appear on the line.
    It is likely to be the waste of resources.
2. Each socket requires an allocation of stack memory whose default size ranges from 64 KB to 1 MB, depending on the OS.
3. Although the JVM can physically support a very large number of threads,
    Context switching overhead becomes problematic before reaching the limit, typically around 10000 conn.

For these reasons, programmers found more efficient way using non-blocking calls.
They delegated the complex process of managing multiple I/O threads to the OS by using the system's event notification API,
which indicates which sockets have data ready for reading or writing. And that is `JAVA NIO`.

Unlike the diagram where each socket connected to its own thread,
a selector sits in front of the thread and identifies which socket is ready for reading and writing among multiple sockets.

By doing so, We can handle multiple connections with fewer threads, and reduce the overhead from context switching.

Even though many programs are built using Java NIO directly, handling sockets efficiently and safely remains a challenge,
especially for programs under heavy load.

1. 어느 시점에 수 많은 스레드들은 언제 등장할지도 모르는 입/출력 데이터를 기다리고만 있을 것이다. 자원의 손실로 이어진다. 
2. 매 소켓마다 스택 메모리의 할당이 필요하다. OS 따라 스택 메모리의 할당 크기는 64kb - 1MB 정도 된다. 
3. JVM이 많은 수의 스레드를 처리할 수 있지만, 컨텍스트 스위칭 오버헤드가 문제를 야기할 수 있다. 일반적으로는 약 10000 개의 연결 정도에서 문제가 될 수 있다. 

이러한 이유로 프로그래머들은 더 효율적인 I/O 방식을 찾았다. 그 방법은 수 많은 스레드를 프로세스에서 처리하지 않고, OS에게 위임하는 것이다. 
OS는 kqueue 등의 자료구조를 통해 JVM보다 더 효율적으로 수 많은 스레드를 처리할 수 있고, 이벤트 알림 API를 통해 입/출력이 준비된 소켓을 프로세스에게 알려줄 수 있다. 이것이 `JAVA NIO`이다.

기존 차단(blocking) 방식에서 하나의 스레드가 하나의 소켓과 매칭되었던 것과는 다르게, 
비차단(non-blocking) 방식에서는 하나의 소켓 앞에 선택자(selector)를 둠으로써, 하나에 소켓에서 여러 개의 스레드를 연결하고 이벤트 발생 여부를 전달할 수 있게 되었다. 

이러한 방식으로 수 많은 커넥션을 더 적은 스레드로 관리할 수 있게 되었고, 컨텍스트 스위칭의 오버헤드도 줄일 수 있었다.

그럼에도 `JAVA NIO`를 활용해 직접 소켓을 안전하고 효율적으로 관리하는 것은 (특히 고부하 프로그램에서) 프로그래머들에게 어려운 일이었다. 

네티는 이런 어려움을 해결하고자 탄생했다. 보다 고수준에서, 안전하고 효율적으로 소켓을 관리할 수 있는 기능을 제공한다.

## Netty’s core components

### 1. Channel

A `Channel` is an open connection to an entity such as a hardware device, a file, a network socket, or a program component, enabling one or more distinct I/O operations.
For now, think of it as a vehicle for inbound(outbound) data. As such, it can be open(connected) or closed(disconnected). 

채널은 하드웨어 장치, 파일, 네트워크 소켓, 프로그램 구성요서에 대한 열린 연결이다.
이 연결로 하나 이상의 독립적인 입출력 작업을 수행할 수 있다. 현재로서는 데이터의 입출력(수신 또는 송신)을 위한 수단으로 생각하면 된다.
따라서, `Channel`은 열려 있거나(Connected) 닫혀 있을 수(Disconnected) 있다.

### 2. Callbacks

A `Callback` is just a method that you can pass to another method. This allows the latter to call the former at the appropriate time. 
Callbacks are commonly used in programming to let other parts of the code know when an operation has been finished.

Netty uses `Callback` internally to handle events; when a `Callback` is triggered, the event can be handled by an implementation of `ChannelHandler`.
For example, when a new connection has been established, the `ChannelHandler` callback `channelActive()` will be called and will print a message.

콜백은 다른 메서드로 전달할 수 있는 메서드이다. 이로써, 다른 메서드에서 해당 메서드를 호출할 수 있다. 
콜백은 일반적으로 한 작업이 완료되었을 때, 다른 부분의 코드가 해당 작업의 완료를 알 수 있도록 하기 위해 사용된다. 

네티는 콜백을 이벤트 핸들링을 위해 사용한다. 콜백이 트리거되면 해당 이벤트는 `ChannelHandler`의 구현체에 의해 다뤄진다. 
예를 들어, 새로운 연결이 설정되면 `ChannelHandler`의 콜백인 `channelActive()` 가 호출되어 메세지를 프린트한다.

```java
// channelActive()는 새로운 connection 생성시 호출된다. 

public class ConnectHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelActive(ConnectHandlerContext ctx)
        throws Exception {
        System.out.println(
                "Client " + ctx.channel().remoteAddress() + " connected"); 
    }
}
```

### 3. Future

A Future provides another way to notify an application when an operation has completed.
This object allows us to access the result of an asynchronous operation that will be completed at some point in the future.

The JDK ships with interface java.util.concurrent.Future, but the provided implementations allow you only to check manually whether the operation has completed or to block until it does.
This is quite cumbersome, so Netty provides its own implementation, `ChannelFuture`, for use when an asynchronous operation is executed.

`ChannelFuture` provides additional methods that allow us to register one or more `ChannelFutureListener` instances. 
The listener’s callback method, `operationComplete()`, is called when the operation has completed. 
The listener can then determine whether the operation completed successfully or with an error. If the latter, we can retrieve the Throwable that was produced.
In short, the notification mechanism provided by the `ChannelFutureListener` eliminates the need for manually checking operation completion.

퓨처는 한 작업이 완료되었음을 어플리케이션에 알릴 수 있는 다른 방법이다. 이 오브젝트는 비동기적 작업이 끝났을 시점에, 해당 결과로의 접근을 제공한다.  

JDK에서는 `java.util.concurrent.Future` 라는 인터페이스를 구현하지만, 해당 구현체를 사용 시 작업이 끝났는지, 끝날 때 까지 차단(block)할 것인지를 직접 확인해야한다. 
이것은 꽤나 까다로우므로, 네티는 `ChannelFuture`라는 자체 구현체를 사용한다. 

`ChannelFuture`는 하나 이상의 `ChannelFutureListener` 인스턴스를 등록할 수 있는 추가 메서드를 제공한다. 
`ChannelFutureListener`의 콜백 메서드인 `operationComplete()`는 작업이 완료되었을 때 호출된다. 
리스너는 작업이 성공적으로 완료되었는지, 아니면 오류와 함께 완료되었는지를 판단할 수 있다. 오류가 발생한 경우, 생성된 Throwable 객체를 조회할 수 있다.
요약하자면, `ChannelFutureListener`가 제공하는 알림 메커니즘은 작업 완료 여부를 수동으로 확인할 필요를 제거해준다.

![img_1](/img/chapter01_1.jpg)

As shown above, a `ChannelFuture` is returned as part of an I/O operation.
In this case, `connect()` returns immediately without blocking, and the connection process completes in the background. 
Although the exact time when the connection will be established is not known, this detail is abstracted away from the code. 
Since the thread is not blocked while waiting for the operation to complete, it can perform other tasks in the meantime, thus using resources more efficiently.

![img_2](/img/chapter01_2.jpg)

This is how to utilize the `ChannelFutureListener`.

You can register a new `ChannelFutureListener` with the `ChannelFuture` returned by the `connect()` call.
When the connection established, the listener will be notified, and you can check the status. 
If the operation is successful, you can write data to the Channel. Otherwise, you retrieve the `Throwable` from the `ChannelFuture`.
The Error Handling is upto you. You can try re-connection, or connect to another remote peer after connection failure.

In summary,  `ChannelFutureListener` is more advanced version of `Callback` because it provides enhanced capabilities for handling asynchronous operations. 
It allows for detailed monitoring of operation completion, supports event-driven processing, and improves resource management by avoiding thread blocking.

### 4. Events and handlers

Netty uses distinct events to notify us about changes of state or the status of operations. 
This allows us to trigger the appropriate action based on the event that has occurred. 
Such actions might include

- Logging
- Data transformation
- Flow-control
- Application logic

Netty is a networking framework, so events are categorized by their relevance to inbound or outbound data flow.
Events that may be triggered by `inbound` data or an associated change of state include

- Active or inactive connections
- Data reads
- User events
- Error events

An `outbound` event is the result of an operation that will trigger an action in the future, which may be

- Opening or closing a connection to a remote peer
- Writing or flushing data to a socket

Every event can be dispatched to a user-implemented method of a handler class. 
This is a good example of an event-driven paradigm translating directly into application building blocks.

![img_3](/img/chapter01_3.jpg)

Netty’s ChannelHandler provides the basic abstraction for handlers like the ones shown above.
We’ll have a lot more to say about ChannelHandler in due course, but for now you can think of each handler instance as a kind of callback to be executed in response to a specific event.

Netty provides an extensive set of predefined handlers that you can use out of the box, including handlers for protocols such as HTTP and SSL/TLS.
Internally, `ChannelHandlers` use events and futures themselves, making them consumers of the same abstractions your applications will employ.


###  Putting it all together
#### Futures, callbacks, and handlers

Netty’s asynchronous programming model is built on the concepts of Futures and callbacks, with the dispatching of events to handler methods happening at a deeper level. 
Taken together, these elements provide a processing environment that allows the logic of your application to evolve independently of any concerns with network operations. 
This is a key goal of Netty’s design approach.

Intercepting operations and transforming inbound or outbound data on the fly requires only that you provide callbacks or utilize the Futures that are returned by operations. 
This makes chaining operations easy and efficient and promotes the writing of reusable, generic code.

Netty's asynchronous programming model is based on `Future` and `Callback`, and the dispatching of events to handler methods happens at a deeper level.
With these components, we can develop the logic of our application independently of network operations.

Intercepting operations and transforming inbound or outbound data on the fly requires only that you provide callbacks or utilize the Futures that are returned by operations.
This makes chaining operations easy and efficient and promotes the writing of reusable, generic code.

#### Selectors, events, and event loops

Netty abstracts the Selector away from the application by firing events, eliminating all the handwritten dispatch code that would otherwise be required. 
Under the covers, an `EventLoop` is assigned to each Channel to handle all the events, including

- Registration of interesting events
- Dispatching events to ChannelHandlers
- Scheduling further actions

The EventLoop itself is driven by only one thread that handles all the I/O events for one Channel and does not change during the lifetime of the EventLoop. 
This simple and powerful design eliminates any concern you might have about synchronization in your ChannelHandlers, so you can focus on providing the right logic to be executed when there is interesting data to process. 
