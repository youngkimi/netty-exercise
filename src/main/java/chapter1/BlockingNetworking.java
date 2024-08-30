package chapter1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.Channel;

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
