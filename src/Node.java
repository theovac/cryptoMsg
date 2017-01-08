import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.Buffer;
import java.util.Queue;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadFactory;

/**
 * Created by theovac on 1/8/17.
 */
public class Node {
    private static ServerSocket serverSocket;
    private static Socket inClientSocket;
    private static Socket outClientSocket;
    private static BufferedReader in;
    private static PrintWriter out;
    private static Scanner scanner = new Scanner(System.in);
    private static String customInput;
    private static Scanner connectToServerScanner;
    private static boolean interrupt = false;

    public Node(int port) throws Exception{
        serverSocket = new ServerSocket(port);
    }

    public static void main(String[] args) throws Exception {
        Queue<String> userInputLines = new ConcurrentLinkedQueue<>();

        Thread readSystemIn = new Thread(new Runnable() {
            @Override
            public void run() {
                Scanner scanner = new Scanner(System.in);
                while(true) {
                    if (scanner.hasNextLine()) {
                        userInputLines.add(scanner.nextLine());
                    }
                }
            }
        });




        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                String serverIP = null;
                String serverLine = null;
                int serverPort = 0;

                System.out.println("Enter server IP: ");
                while (true) {
                    if (interrupt) {
                        break;
                    }
                    if ((serverIP = userInputLines.poll()) != null) {
                        System.out.println("Enter server port: ");
                        break;
                    }
                }

                while (true) {
                    if (interrupt) {
                        break;
                    }

                    if ((serverLine = userInputLines.poll()) != null) {
                        serverPort = new Scanner(serverLine).nextInt();
                        break;
                    }
                }

                if (!interrupt) {
                    try {
                        outClientSocket = new Socket(serverIP, serverPort);
                        in = new BufferedReader(new InputStreamReader(outClientSocket.getInputStream()));
                        out = new PrintWriter(outClientSocket.getOutputStream(), true);
                    } catch (IOException e) {
                        System.out.println(e);
                    }
                    Thread sendMessages = new Thread(new Runnable() {
                        String message = null;
                        @Override
                        public void run() {
                            while(true) {
                                if ((message = userInputLines.poll()) != null) {
                                    out.println(message);
                                }
                            }
                        }
                    });
                    sendMessages.start();

                    while (true) {
                        try {
                            String incomingMessage = in.readLine();
                            System.out.println(incomingMessage);
                        } catch (IOException e) {
                            System.out.println(e);
                        }
                    }
                }
            }
        });

        // Initialize Node on a specified port.
        System.out.println("Enter server port: ");
        int port = scanner.nextInt();
        scanner.nextLine();

        Node node = new Node(port);
        readSystemIn.start();
        t.start();
        System.out.println("Server started.");

        inClientSocket = node.serverSocket.accept();
        if (outClientSocket == null) {
            in = new BufferedReader(new InputStreamReader(inClientSocket.getInputStream()));
            out = new PrintWriter(inClientSocket.getOutputStream(), true);
            interrupt = true;

            Thread sendMessages = new Thread(new Runnable() {
                String message = null;
                @Override
                public void run() {
                    while(true) {
                        if ((message = userInputLines.poll()) != null) {
                            out.println(message);
                        }
                    }
                }
            });
            sendMessages.start();

            while (true) {
                try {
                    String incomingMessage = in.readLine();
                    System.out.println(incomingMessage);
                } catch (IOException e) {
                    System.out.println(e);
                }
            }
        }
    }
}
