import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.Socket;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECField;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Hashtable;
import java.util.Scanner;
import java.util.concurrent.RunnableFuture;
import java.security.*;
import java.util.concurrent.locks.Lock;
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
    String rsaHeader = "-----BEGIN RSA PUBLIC KEY-----";
    String rsaFooter = "-----END RSA PUBLIC KEY-----";
    String clientPublicKey;
    PrivateKey clientPrivateKey;
    Cipher cipher = Cipher.getInstance("RSA");
    Cipher decipher = Cipher.getInstance("RSA");

    // Keep the public keys of all the clients this client has messaged
    Hashtable<String, String> nameKeyTable = new Hashtable<>();

    public Client() throws Exception{

        // Get the server IP
        System.out.println("Enter server IP: ");
        String serverIP = new String(userInput.next());

        //System.out.println("-DEBUG- Got server IP: " + serverIP);
        keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();

        clientPublicKey = rsaHeader + '\n' + new BASE64Encoder().encode(keys.getPublic().getEncoded()) + '\n' + rsaFooter;
        clientPrivateKey = keys.getPrivate() ;

        //System.out.println("Private key: \n" + privateKey);
        System.out.println("Public key: \n" + clientPublicKey);
        System.out.println("Public key: \n" + keys.getPublic());

        // Initialize socket and I/O streams for the clients
        clientSocket = new Socket(serverIP, 9999);
        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        out =  new PrintWriter(clientSocket.getOutputStream(), true);

    }

    // Handles client name setting and message processing.
    public void handler() throws Exception {
        Scanner scanner = new Scanner(System.in);
        boolean firstTry = true;
        int serverResponseCode;
        String clientStatus = null;
        boolean serverReadLock = true;


        while (true) {
            // First the user need to set the username he wants to user to connect to the chat server.

            System.out.println("Enter username: ");
            String username = scanner.next();
            scanner.nextLine(); // Finish reading the line that contained the username to avoid future problems.
            // Send a connection request to the server in which the client's username and the public key are included.
            out.println("CONNECT&" + username + '\n' + this.clientPublicKey);

            // Check if the server accepted the connection request and if so stop sending new ones.
            serverResponseCode = new Scanner(in.readLine()).nextInt();
            if (serverResponseCode == 200) {
                clientStatus = "connected";
                break;
            }
        }

        Thread t = new Thread(new Runnable() {
            @Override
            public void run(){
                while(true) {
                        try {
                            String incomingMessage = in.readLine();
                            System.out.println(incomingMessage);
                        } catch (IOException e) {
                            System.out.println(e);
                        }
                    }
            }
        });
        t.start();

        while (true) {
            if (clientStatus.equals("connected")) {
                // Ask the server for a list of all the connected clients.
                out.println("GET&userlist");
                String[] userlist = in.readLine().split("&");
                // Print the user list
                System.out.println("Connected users: ");
                for (String user : userlist) {
                    System.out.println("- " + user);
                }
                // Get input from the user to determine which the next action should be.
                String action = scanner.next();
                if (action.equals("message") || action.equals("m")) {
                    String receiverUsername = scanner.next();
                    scanner.nextLine();
                    out.println("MESSAGE&" + receiverUsername);
                    // TODO: Get receiver public key.
                }
                serverResponseCode = new Scanner(in.readLine()).nextInt();
                if (serverResponseCode == 200) {
                    System.out.println("DEBUG Ready to start messaging.");
                    clientStatus = "messaging";
                }
            } else {
                String outgoingMessage = scanner.nextLine();
                System.out.println(outgoingMessage);
                out.println(outgoingMessage);
            }
        }
    }

    private void readUserList() {

    }

    public void getMessage() throws Exception{
        String publicKeyStr;
        X509EncodedKeySpec publicKeySpec;
        PublicKey publicKey;

        System.out.println("Enter receiver username: ");

//      System.out.println("-DEBUG- Waiting for input." + '\n');
        String receiverUsername = userInput.next();
        System.out.println("DEBUG Got receiver " + receiverUsername);
        userInput.nextLine();

        if (nameKeyTable.contains(receiverUsername)) {
            System.out.println("DEBUG Receiver key known.");
            publicKeyStr = nameKeyTable.get(receiverUsername);
        } else {
            out.println("get_key " + receiverUsername);
            publicKeyStr = readPublicKey();
            System.out.println("DEBUG Got key");
            System.out.println(publicKeyStr);
            nameKeyTable.put(receiverUsername, publicKeyStr);
        }
        publicKeySpec = new X509EncodedKeySpec(new BASE64Decoder().decodeBuffer(publicKeyStr));
        System.out.println("DEBUG Created KeySpec");
        publicKey = KeyFactory.getInstance("RSA").generatePublic(publicKeySpec);
        System.out.println("DEBUG Regenerated public key");
        System.out.println(publicKey);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);

        System.out.println("Enter message: ");

//      System.out.println("-DEBUG- Got input." + input + '|' + '\n');
        String message = userInput.nextLine();

        if (message != null && !message.equals(" ")) {
            byte[] cipherText = cipher.doFinal(message.getBytes());
            out.println("message" + " " + receiverUsername + " " + new BASE64Encoder().encode(cipherText));
        }
    }

    public String readPublicKey() throws Exception{
        String line;
        String publicKey = "";
        boolean read = false;

        // Read the part of the key between the RSA header and footer
        while (true) {
            line = in.readLine();
            if (line.equals("-----END RSA PUBLIC KEY-----")) {
                break;
            } else if (line.equals("-----BEGIN RSA PUBLIC KEY-----")) {
                read = true;
            } else if (read) {
                if (publicKey != "") {
                    publicKey += '\n';
                }
                publicKey += line;
            }
        }

        return publicKey;
    }

    public static void main(String args[]) throws Exception{
        Client client = new Client();
        client.handler();
    }
}
