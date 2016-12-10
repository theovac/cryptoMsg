import sun.misc.BASE64Encoder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.Socket;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Scanner;
import java.util.concurrent.RunnableFuture;
import java.security.*;
import javax.crypto.*;

/**
 * Created by theovac on 11/29/16.
 */
public class Client {
    Scanner userInput = new Scanner(System.in);
    Socket clientSocket;
    BufferedReader in;
    PrintWriter out;
    KeyPair keys;

    public Client() throws IOException, NoSuchAlgorithmException, java.security.spec.InvalidKeySpecException{

        // Get the server IP
        System.out.println("Enter server IP: ");
        String serverIP = new String(userInput.next());
//        System.out.println("-DEBUG- Got server IP: " + serverIP);
        try {
            keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            System.out.println(e);
        }
        System.out.println("Private key: \n" + new BASE64Encoder().encode(keys.getPrivate().getEncoded()));
        System.out.println("Public key: \n" + new BASE64Encoder().encode(keys.getPublic().getEncoded()));
        // Initialize socket and I/O streams for the clients1
        clientSocket = new Socket(serverIP, 9999);
        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        out =  new PrintWriter(clientSocket.getOutputStream(), true);

    }

    // Handles client name setting and message processing.
    public void conHandler() throws IOException{
        boolean firstTry = true;

        while(true) {
            String line = in.readLine();

            if (line.startsWith("set_name")) {
                if (firstTry) {
                    String name = getName();
                    // Only submit non null names to the server.
                    if (name != null) {
                        out.println(name);
                        firstTry = false;
                    }
                }
                else {
                    /* Only non null messages are submitted so if a name has already been
                       submitted and hasn't been accepted, it means it is already taken.
                     */
                    System.out.println("Username is already taken.");
                    String name = getName();
                    if (name != null) {
                        out.println(getName());
                    }
                }
            } else if (line.startsWith("name_accepted")) {
                // Get messages from the user in a separate thread.
                Thread t = new Thread(new Runnable() {
                    public void run() {
                        while (true) {
                            try {
//                              System.out.println("-DEBUG- Getting message to send.");
                                System.out.print("\n>");
                                getMessage();
                            } catch (IOException e) {
                                System.out.println(e);
                            }
                        }
                    }
                });
                t.start();
//                System.out.println("-DEBUG- Moved past thread.");
            } else if (line.startsWith("message")) {
//                System.out.println("-DEBUG- Waiting for incoming message." + '\n');
                // Print incoming messages.
                System.out.print(line.substring(8) + '\n' + '>');

            }
        }
    }

    public String getName() {
        System.out.println("Enter username: ");
        String name = userInput.next();
        userInput.nextLine();

        return name;
    }

    public void getMessage() throws IOException{
//        System.out.println("-DEBUG- Waiting for input." + '\n');
        String input = userInput.nextLine();
//        System.out.println("-DEBUG- Got input." + input + '|' + '\n');

        if (input != null && !input.equals(" ")) {
            out.println(input);
        }
    }

    public static void main(String args[]) throws Exception{
        Client client = new Client();
        client.conHandler();
    }
}
