import java.io.*;
import java.net.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A communication protocol where a server handles multiple
 * client connections and allows the clients to exchange messages.
 */
public class Server {

    public static final int port = 9999;

    public Server() {
    }

    // Names of all the clients in the room
    public static HashSet<String> names = new HashSet<String>();

    // Writers to all the clients in the room.
    public static HashSet<PrintWriter> writers = new HashSet<PrintWriter>();

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
        String name;
        private BufferedReader in;
        private PrintWriter out;

        public Handler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        public void run() {
//            System.out.println("-DEBUG- Created Handler object.");
            try {
                // Create input and output streams for this socket
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                out = new PrintWriter(clientSocket.getOutputStream(), true);

                /* Now the client must choose a unique username. The handler will keep
                  prompting, until a valid username is entered. */
                while (true) {
                   // Request the client to set a name.
//                    System.out.println("-DEBUG- Waiting for name.");
                    out.println("set_name");
                    name = in.readLine();
//                    System.out.println("-DEBUG- Got name: " + name + '\n');

                    synchronized (names) {
                        // Check in name is valid
                        if (!names.contains(name) && name != null) {
                            names.add(name);
                            writers.add(out);
//                            System.out.println("-DEBUG- Name is valid. \n");
                            out.println("name_accepted");
                            break;
                        }
//                        System.out.println("-DEBUG- Name is invalid. \n");
                    }
                }
//                System.out.println("-DEBUG-" + writers.size() + " writers added.");

                // The handler broadcasts the clients messages.
                while (true) {
                    String input = in.readLine();
//                    System.out.println("-DEBUG- Input: " + input);
                    if (input != null && !input.equals(" ")) {
                        for (PrintWriter writer : writers) {
                            writer.println("message " + name + ": " + input);
                        }
                    }
                }
           } catch (IOException e) {
               System.out.println(e);
           } finally {
               /* This is cleanup code to be executed when the client exits
                  for whatever reason. */

               // If the client managed to register a username remove it
               if (name != null) {
                   names.remove(name);
               }

               // And it's writer
               if (out != null) {
                   writers.remove(out);
               }

               // Close the client's socket
               try {
                   clientSocket.close();
               } catch (IOException e) {
                   System.out.println(e);
               }
           }
        }
    }
}
