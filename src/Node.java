import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyFactory;
import java.security.KeyPair; import java.security.KeyPairGenerator; import java.security.PublicKey;
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
    private static boolean clientSessionStarted = false;
    private static boolean serverSessionStarted = false;
    private static Queue<String> input = new ConcurrentLinkedQueue<>();
    private static String rsaKeyHeader = "-----BEGIN RSA PUBLIC KEY-----";
    private static String rsaKeyFooter = "-----END RSA PUBLIC KEY-----";
    private static String rsaMessageHeader = "-----BEGIN ENCRYPTED MESSAGE-----";
    private static String rsaMessageFooter = "-----END ENCRYPTED MESSAGE-----";
    private PublicKey partnerPublicKey;

    public Node(int serverPort) throws Exception{
        this.keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        ServerThread serverThread = new ServerThread(serverPort);
        ClientThread clientThread = new ClientThread();
        Thread readSystemInThread = new Thread(new SystemInReader());
        readSystemInThread.start();
        clientThread.start();
        serverThread.start();
    }

    private class ServerThread extends Thread {
        ServerSocket serverSocket;
        public ServerThread(int port) throws IOException{
            this.serverSocket = new ServerSocket(port);
        }

        public void run() {
            Socket connectionSocket;
            // Listen for connection requests.
            try {
                connectionSocket = this.serverSocket.accept();
                serverSessionStarted = true;

                if (!clientSessionStarted) {
                    Thread incomingMessageThread = new IncomingMessageThread(connectionSocket);
                    Thread outgoingMessageThread = new OutgoingMessageThread(connectionSocket);
                    incomingMessageThread.start();
                    outgoingMessageThread.start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ClientThread extends Thread {
        public ClientThread(){
        }

        public void run() {
            String connectionIP = null;
            int connectionPort = 0;
            Socket connectionSocket;
            String portLine;

            System.out.print("Enter IP to connect to: ");
            // Read user input and initiate connection.
            while(!serverSessionStarted) {
                if ((connectionIP = input.poll()) != null) {
                    System.out.print("Enter port to connect to: ");
                    break;
                }
            }
            while(!serverSessionStarted) {
                if ((portLine = input.poll()) != null) {
                    connectionPort = new Integer(new Scanner(portLine).next());
                    break;
                }
            }
            if (!serverSessionStarted) {
                try {
                    connectionSocket = new Socket(connectionIP, connectionPort);

                    Thread incomingMessageThread = new IncomingMessageThread(connectionSocket);
                    incomingMessageThread.start();

                    Thread outgoingMessageThread = new OutgoingMessageThread(connectionSocket);
                    outgoingMessageThread.start();
                    clientSessionStarted = true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class IncomingMessageThread extends Thread {
        Socket connectionSocket;
        BufferedReader inStream;
        public IncomingMessageThread(Socket connectionSocket) throws IOException{
            this.connectionSocket = connectionSocket;
            this.inStream = new BufferedReader(new InputStreamReader(this.connectionSocket.getInputStream()));
        }
        public void run() {
            String message;
            // RSA key exchange.
            try {
            String partnerPublicKeyBase64 = readField(inStream);
            Node.this.partnerPublicKey = KeyFactory.getInstance("RSA")
                                      .generatePublic(new X509EncodedKeySpec(Base64.getDecoder()
                                      .decode(partnerPublicKeyBase64)));
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                while (true) {
                        message = readField(this.inStream);
                        Cipher decryptionCipher = Cipher.getInstance("RSA");
                        decryptionCipher.init(Cipher.PRIVATE_KEY, keys.getPrivate());
                        String decryptedMessage = new String(decryptionCipher.doFinal(Base64.getDecoder().decode(
                                                            message)));
                        System.out.println(connectionSocket.getRemoteSocketAddress().toString() +
                                           ": " + decryptedMessage);
                }
            } catch (Exception e) {
                System.exit(0);
               e.printStackTrace();
            }
        }
    }

    private class OutgoingMessageThread extends Thread {
        Socket connectionSocket;
        PrintWriter outStream;


        public OutgoingMessageThread(Socket connectionSocket) throws IOException{
            this.connectionSocket = connectionSocket;
            this.outStream = new PrintWriter(this.connectionSocket.getOutputStream(), true);
        }
        public void run() {
            String message;
            // RSA key exchange.
            String publicKey = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
            outStream.println(rsaKeyHeader + '\n' + publicKey + '\n' + rsaKeyFooter);

            try {
                System.out.println("Connected to " +
                                   connectionSocket.getRemoteSocketAddress().toString() +
                                   " on port " + connectionSocket.getPort());
                while (true) {
                    if (Node.this.partnerPublicKey != null && (message = input.poll()) != null) {
                        Cipher encryptionCipher = Cipher.getInstance("RSA");
                        encryptionCipher.init(Cipher.PUBLIC_KEY, Node.this.partnerPublicKey);
                        String encryptedMessage = Base64.getEncoder().encodeToString(
                                                  encryptionCipher.doFinal(message.getBytes()));
                        // Write to socket.
                        outStream.println(rsaMessageHeader + '\n' +
                                          encryptedMessage + '\n' + rsaMessageFooter);
                    }
                }
            } catch (Exception e) {
                System.exit(0);
                e.printStackTrace();
            }
        }
    }

    private static class SystemInReader implements Runnable {
        /** Reads every line typed in the system input and stores it in a queue.
         * We do this so as to be able to use the interruptable Queue.poll() method to get the
         * next input line.
         */

        public SystemInReader() {
        }

        @Override
        public void run() {
            Scanner scanner = new Scanner(System.in);
            while(true) {
                if (scanner.hasNextLine()) {
                    input.add(scanner.nextLine());
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter server port:");
        Node baseNode = new Node(scanner.nextInt());
    }

    private String readField(BufferedReader inStream) throws Exception{
        String line;
        String textField = "";
        boolean read = false;
        String rsaKeyHeader = Node.this.rsaKeyHeader;
        String rsaKeyFooter = Node.this.rsaKeyFooter;
        String rsaMessageHeader = Node.this.rsaMessageHeader;
        String rsaMessageFooter = Node.this.rsaMessageFooter;

        // Read the given header and footer.
        while (true) {
            line = inStream.readLine();
            if (line.equals(rsaKeyFooter) || line.equals(rsaMessageFooter)) {
                break;
            } else if (line.equals(rsaKeyHeader) || line.equals(rsaMessageHeader)) {
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
