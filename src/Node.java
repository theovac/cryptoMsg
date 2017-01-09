import com.sun.org.apache.xml.internal.dtm.ref.ExpandedNameTable;
import com.sun.org.apache.xml.internal.security.utils.Base64;
import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Queue;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.crypto.Cipher;

/**
 * Created by theovac on 1/8/17.
 */
public class Node {
    public static KeyPair keys;
    private static ServerSocket serverSocket;
    private static Socket inClientSocket;
    private static Socket outClientSocket;
    private static BufferedReader in;
    private static PrintWriter out;
    private static Scanner scanner = new Scanner(System.in);
    private static boolean interrupt = false;
    private static Queue<String> userInputLines = new ConcurrentLinkedQueue<>();
    private static String rsaKeyHeader = "-----BEGIN RSA PUBLIC KEY-----";
    private static String rsaKeyFooter = "-----END RSA PUBLIC KEY-----";
    private static String rsaMessageHeader = "-----BEGIN ENCRYPTED MESSAGE-----";
    private static String rsaMessageFooter = "-----END ENCRYPTED MESSAGE-----";
    private static PublicKey partnerPublicKey;

    public Node(int port) throws Exception{
        serverSocket = new ServerSocket(port);
        keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        System.out.println(new BASE64Encoder().encode(keys.getPublic().getEncoded()));
    }

    private static class readSystemIn implements Runnable {
        /* Reads every line typed in the system input and stores it in a queue.
           We do this so as to be able to use the interruptable Queue.poll() method to get the
           next input line. */
        @Override
        public void run() {
            Scanner scanner = new Scanner(System.in);
            while(true) {
                if (scanner.hasNextLine()) {
                    userInputLines.add(scanner.nextLine());
                }
            }
        }
    }

    private static class userInitSession implements Runnable {
        /* Enables the user to connect to a server and start messaging.
           if a connection request to this Node's server socket is accepted, this
           thread is interrupted.
        */
        @Override
        public void run() {
            String serverIP = null;
            String serverLine = null;
            int serverPort = 0;

            // The server's IP and connection port are provided by the user.
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
                    // Create the socket that will be user for messaging and initiate reader and writer for it.
                    outClientSocket = new Socket(serverIP, serverPort);
                    in = new BufferedReader(new InputStreamReader(outClientSocket.getInputStream()));
                    out = new PrintWriter(outClientSocket.getOutputStream(), true);

                    // Sends the public key in base64 format to the other node.
                    String publicKey = new BASE64Encoder().encode(keys.getPublic().getEncoded());
                    out.println(rsaKeyHeader + '\n' + publicKey + '\n' + rsaKeyFooter);

                    // Reads the public key from the connection partner node and decodes it.
                    String partnerPublicKeyBase64 = readField(rsaKeyHeader, rsaKeyFooter);
                    System.out.println(partnerPublicKeyBase64);
                    partnerPublicKey = KeyFactory.getInstance("RSA").generatePublic(
                            new X509EncodedKeySpec(Base64.decode(partnerPublicKeyBase64)));


                } catch (Exception e) {
                    System.out.println(e);
                }

                // Start messaging
                initSession();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Thread readSystemInThread = new Thread(new readSystemIn());
        Thread userInitSessionThread = new Thread(new userInitSession());

        System.out.println("Enter server port: ");
        int port = scanner.nextInt();
        scanner.nextLine();

        // Initialize Node on the specified port.
        Node node = new Node(port);
        readSystemInThread.start();
        userInitSessionThread.start();

        System.out.println("Server started.");

        inClientSocket = node.serverSocket.accept();
        if (outClientSocket == null) {
            in = new BufferedReader(new InputStreamReader(inClientSocket.getInputStream()));
            out = new PrintWriter(inClientSocket.getOutputStream(), true);
            interrupt = true;

            // Reads the public key from the connection partner node and decodes it.
            String partnerPublicKeyBase64 = readField(rsaKeyHeader, rsaKeyFooter);
            System.out.println(partnerPublicKeyBase64);
            partnerPublicKey = KeyFactory.getInstance("RSA").generatePublic(
                    new X509EncodedKeySpec(Base64.decode(partnerPublicKeyBase64)));

            // Sends the public key in base64 format to the other node.
            String publicKey = new BASE64Encoder().encode(keys.getPublic().getEncoded());
            out.println(rsaKeyHeader + '\n' + publicKey + '\n' + rsaKeyFooter);

        }
        initSession();
    }

    public static void initSession() {
        Thread sendMessages = new Thread(new Runnable() {
            String message = null;
            @Override
            public void run() {
                try {
                    while (true) {
                        if ((message = userInputLines.poll()) != null) {
                            // Encrypt the message before sending it.
                            Cipher encryptionCipher = Cipher.getInstance("RSA");
                            encryptionCipher.init(Cipher.PUBLIC_KEY, partnerPublicKey);
                            String encryptedMessage = new BASE64Encoder().encode(encryptionCipher.doFinal(message.getBytes()));
                            System.out.println("\nEncrypted message: \n" + encryptedMessage);

                            out.println(rsaMessageHeader + '\n' + encryptedMessage + '\n' + rsaMessageFooter);
                        }
                    }
                } catch (Exception e) {
                    System.out.println(e);
                }
            }
        });
        sendMessages.start();

        while (true) {
            try {
                String incomingMessage = readField(rsaMessageHeader, rsaMessageFooter);
                System.out.println("\n\nEncrypted message: \n" + incomingMessage);

                Cipher decryptionCipher = Cipher.getInstance("RSA");
                decryptionCipher.init(Cipher.PRIVATE_KEY, keys.getPrivate());

                String decryptedMessage = new String(decryptionCipher.doFinal(Base64.decode(incomingMessage)));
                System.out.println("\nDecrypted message: \n" + decryptedMessage);
            } catch (Exception e) {
                System.out.println(e);
            }
        }
    }

    public static String readField(String header, String footer) throws Exception{
        String line;
        String textField = "";
        boolean read = false;

        // Read the part of the key between the RSA header and footer
        while (true) {
            line = in.readLine();
            if (line.equals(footer)) {
                break;
            } else if (line.equals(header)) {
                read = true;
            } else if (read) {
                if (textField != "") {
                    textField += '\n';
                }
                textField += line;
            }
        }

        return textField;
    }
}
