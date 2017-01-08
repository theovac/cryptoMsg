import sun.misc.BASE64Decoder;

import java.io.*;
import java.net.*;
import java.nio.CharBuffer;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/**
 * A communication protocol where a server handles multiple
 * client connections and allows the clients to exchange messages.
 */
public class Server {

    public static final int port = 9999;

    public Server() {
    }

    // Names of all the clients in the room
    public static Hashtable<String, String> nameKeyTable = new Hashtable<String, String>();

    // Writers to all the clients in the room.
    public static Hashtable<String, PrintWriter> nameWriterTable = new Hashtable<String, PrintWriter>();

    public static void main(String args[]) throws IOException {
        System.out.println("Server running ...");

        /* The server is always listening for connections and assigns
         * a new Handler object to each new client.
         */

        ServerSocket listener = new ServerSocket(port);

        try {
            while (true) {
                Thread t = new Thread(new Handler(listener.accept()));
                t.start();
            }
        } finally {
            listener.close();
        }
    }

    /**
     * A handler object is created by the server to manage a single client
     **/

    public static class Handler implements Runnable{
        private Socket clientSocket;
        private String username = null;
        private String receiverUsername = null;
        private String publicKeyStr;
        private X509EncodedKeySpec publicKeyspec;
        private PublicKey publicKey;
        private BufferedReader in;
        private PrintWriter out;
        private String rsaHeader = "-----BEGIN RSA PUBLIC KEY-----";
        private String rsaFooter = "-----END RSA PUBLIC KEY-----";


        public Handler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        public void run(){
//            System.out.println("-DEBUG- Created Handler object.");
            try {
                // Create input and output streams for this socket
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                out = new PrintWriter(clientSocket.getOutputStream(), true);

                /* Now the client must choose a unique username. The handler will keep
                  prompting, until a valid username is entered. */
                while (true) {
                    String clientRequest = in.readLine();
                    System.out.println("DEBUG Client request: " + clientRequest);
                    // Handle connection request.
                    if (clientRequest.startsWith("CONNECT")) {
                        String[] clientRequestList = clientRequest.split("&");
                        username = clientRequestList[1];
                        publicKeyStr = readPublicKey();
                        System.out.println(publicKeyStr);

                        synchronized (nameKeyTable) {
                            // If username is valid and unique save it along with the client's public key and respond
                            // with success response code.
                            if (!nameKeyTable.contains(username) && username != null) {
                                nameWriterTable.put(username, out);
                                nameKeyTable.put(username, ""); // TODO: Save public key instead of empty string.

                                out.println(200);
                            }
                        }
                    } else if (clientRequest.startsWith("GET")) {
                        String[] clientRequestList = clientRequest.split("&");
                        if (clientRequestList[1].equals("userlist")) {
                            String userlist = String.join("&", nameKeyTable.keySet());
                            System.out.println("DEBUG Userlist: " + userlist);
                            out.println(userlist);
                        }
                    }
                    else if (clientRequest.startsWith("MESSAGE")) {
                        String[] clientRequestList = clientRequest.split("&");
                        System.out.println(clientRequestList[1]);
                        synchronized (nameKeyTable) {
                            for (String name : nameKeyTable.keySet()) {
                                System.out.println(name);
                            }
                            if (nameKeyTable.keySet().contains(clientRequestList[1])) {
                                System.out.println("DEBUG Receiver set.");
                                receiverUsername = clientRequestList[1];
                                out.println(200);
                                // TODO: Send receiver's public key to the client.
                            }
                        }
                    } else if (username != null && receiverUsername != null) {
                        System.out.println("DEBUG User chatting.");
                        nameWriterTable.get(receiverUsername).println(clientRequest);
                    }
                }
           } catch (Exception e) {
               System.out.println(e);
           } finally {
               /* This is cleanup code to be executed when the client exits
                  for whatever reason. */

               // If the client managed to register a username remove it
               if (username != null) {
                   nameKeyTable.remove(username);
               }

               // And it's writer
               if (out != null) {
                   nameWriterTable.remove(username);
               }

               // Close the client's socket
               try {
                   clientSocket.close();
               } catch (IOException e) {
                   System.out.println(e);
               }
           }
        }

        public String readPublicKey() throws Exception{
            System.out.println("DEBUG Reading public key.");
            String line;
            String publicKey = "";
            boolean read = false;

            // Read the part of the key between the RSA header and footer
            while (true) {
                line = in.readLine();
                if (line.equals("-----END RSA PUBLIC KEY-----")) {
                    break;
                }
                else if (line.equals("-----BEGIN RSA PUBLIC KEY-----")) {
                    read = true;
                }
                else if (read) {
                    if (publicKey != "") {
                        publicKey += '\n';
                    }
                    publicKey += line;
                }
            }
            return publicKey;
        }
    }
}
