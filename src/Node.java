import java.io.BufferedReader;
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
import java.util.Base64;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.crypto.Cipher;


/**
 * On the creation of a Node an RSA keypair is generated and the user is prompted to insert a
 * port number on which a ServerSocket that listens for and accepts incoming connection requests
 * is initialized.
 *
 * Then, the user can enter a remote server IP address and port number to create a local socket
 * that will try to connect to the given server or continue listening.
 *
 * After a connection is established, the Node that started the connection request sends its
 * public key and the other node responds doing the same. Finally, a session where RSA encrypted
 * messages are exchanged begins.
 */
public class Node {
    public static KeyPair keys;
    private static ServerSocket serverSocket;
    private static Socket inClientSocket; // Socket created by accepting a connection request.
    private static Socket outClientSocket; // Socket created by making a connection request.
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
    private static boolean verbose = false;

    public Node(int port) throws Exception{
        serverSocket = new ServerSocket(port);
        keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        if (verbose) {
            System.out.println("\nMy public key: \n" +
                    Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()) + '\n');
        }
    }

    private static class readSystemIn implements Runnable {
        /** Reads every line typed in the system input and stores it in a queue.
         * We do this so as to be able to use the interruptable Queue.poll() method to get the
         * next input line.
         */
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
        /* Enables the user to make connection request to a server and start messaging.
           if a connection request to this Node's server socket is accepted, this
           thread is interrupted.
        */
        @Override
        public void run() {
            String serverIP = null;
            String serverLine = null;
            int serverPort = 0;

            // The server's IP and connection port are provided by the user.
            System.out.println("Enter remote server IP: ");
            while (true) {
                if (interrupt) { // Allow the thread to be interrupted.
                    break;
                }
                if ((serverIP = userInputLines.poll()) != null) {
                    System.out.println("Enter remote server port: ");
                    break;
                }
            }
            while (true) {
                if (interrupt) {
                    break;
                }
                if ((serverLine = userInputLines.poll()) != null) {
                    serverPort = new Integer(new Scanner(serverLine).next());
                    break;
                }
            }
            if (!interrupt) {
                try {
                    // Create the socket that will be used for messaging and initiate reader and writer for it.
                    outClientSocket = new Socket(serverIP, serverPort);
                    in = new BufferedReader(new InputStreamReader(outClientSocket.getInputStream()));
                    out = new PrintWriter(outClientSocket.getOutputStream(), true);

                    // Sends the public key in base64 format to the other node.
                    String publicKey = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
                    out.println(rsaKeyHeader + '\n' + publicKey + '\n' + rsaKeyFooter);

                    // Reads the public key from the connection partner node and decodes it.
                    String partnerPublicKeyBase64 = readField(rsaKeyHeader, rsaKeyFooter);
                    partnerPublicKey = KeyFactory.getInstance("RSA").generatePublic(
                            new X509EncodedKeySpec(Base64.getDecoder().decode(partnerPublicKeyBase64)));
		    if (verbose) {
                    	System.out.println("\nReceived public key: \n" + partnerPublicKeyBase64);
		    }
	    	    System.out.println("\nConnected to: " + outClientSocket.getRemoteSocketAddress().toString());
		    System.out.print("\n");

                } catch (Exception e) {
                    e.printStackTrace();
                }

                // Start exchanging messages. 
                initSession(outClientSocket);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Thread readSystemInThread = new Thread(new readSystemIn());
        Thread userInitSessionThread = new Thread(new userInitSession());
	if (args.length != 0 && args[0].equals("verbose")) {
	    verbose = true;
	}

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
            interrupt = true; // Interrupts userInitSessionThread to avoid conflicts.

            // Reads the public key from the connection partner node and decodes it.
            String partnerPublicKeyBase64 = readField(rsaKeyHeader, rsaKeyFooter);
            if (verbose) {
                System.out.println("\nReceived public key: \n" + partnerPublicKeyBase64);
            }
            partnerPublicKey = KeyFactory.getInstance("RSA").generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(partnerPublicKeyBase64)));
	    System.out.println("\nConnected to: " + inClientSocket.getRemoteSocketAddress().toString() +                                 "\n");

            // Sends the public key in base64 format to the other node.
            String publicKey = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
            out.println(rsaKeyHeader + '\n' + publicKey + '\n' + rsaKeyFooter);

        }
        initSession(inClientSocket);
    }

    private static void initSession(Socket clientSocket) {
	// This method is called after key exchange to handle the message exchange.
        Thread sendMessages = new Thread(new Runnable() {
            // Reads message from System.in, then encrypts it and sends it
            String message = null;
            @Override
            public void run() {
                try {
                    while (true) {
                        if ((message = userInputLines.poll()) != null) {
                            // Encrypt the message.
                            Cipher encryptionCipher = Cipher.getInstance("RSA");
                            encryptionCipher.init(Cipher.PUBLIC_KEY, partnerPublicKey);
                            String encryptedMessage = Base64.getEncoder().encodeToString(
                                                        encryptionCipher.doFinal(message.getBytes()));
                            // Write to socket.
                            out.println(rsaMessageHeader + '\n' + 
                                        encryptedMessage + '\n' + rsaMessageFooter);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        sendMessages.start();

        while (true) {
            // Reads incoming messages from the socket, decrypts them and displays them on System.out
            try {
                // Read message.
                String incomingMessage = readField(rsaMessageHeader, rsaMessageFooter);
		if (verbose) {
                    System.out.println("\n\nEncrypted message: \n" + incomingMessage + '\n');
		}
                
                // Decrypt message.
                Cipher decryptionCipher = Cipher.getInstance("RSA");
                decryptionCipher.init(Cipher.PRIVATE_KEY, keys.getPrivate());
                String decryptedMessage = new String(decryptionCipher.doFinal(Base64.getDecoder().decode(
                                                     incomingMessage)));
		if (verbose) {
                    System.out.print("Decrypted message: \n"
                          + clientSocket.getRemoteSocketAddress().toString() + ": " +
                                                                decryptedMessage + '\n');
		} else {
                    System.out.print(clientSocket.getRemoteSocketAddress().toString() + ": " +
                                      decryptedMessage + '\n');
		}

            } catch (Exception e) {
                System.exit(0);
            }
        }
    }

    private static String readField(String header, String footer) throws Exception{
        String line;
        String textField = "";
        boolean read = false;

        // Read the given header and footer.
        while (true) {
            line = in.readLine();
            if (line.equals(footer)) {
                break;
            } else if (line.equals(header)) {
                read = true;
            } else if (read) {
                if (textField != "") {
                    textField += '\n'; // Add new line characters from pretty printing.
                }
                textField += line;
            }
        }

        return textField;
    }
}
