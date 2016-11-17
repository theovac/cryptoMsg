import java.io.DataInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

/**
 * Created by theovac on 11/17/16.
 */
public class Node {

    ServerSocket server = null;
    Socket clientSocket;
    DataInputStream input;
    PrintStream output;
    ServerListener listener;
    Thread t;
    public Node() {
        // Initialize server on object creation.
        try {
            server = new ServerSocket(9999);
            listener = new ServerListener(server);
            t = new Thread(listener);
            t.start();
            System.out.println("debug");
        } catch(IOException e) {
            System.out.println(e);
        }
    }

    public void init() {
        Scanner inputMessage = new Scanner(System.in);

        while(listener.serverConStatus == false) {
            String command = inputMessage.nextLine();
            System.out.println(command);
            if (command.equals("message")) {
                System.out.println("Got command.");
                break;
            } else {
                System.out.println(command);
            }

        }
        if (listener.clientSocket != null) {
            try {
                input = new DataInputStream(listener.clientSocket.getInputStream());
                output = new PrintStream(listener.clientSocket.getOutputStream());
                System.out.println(input.readByte());
            } catch (IOException e) {
                System.out.println(e);
            }
        }
        System.out.println("Connection established.");
    }
}
