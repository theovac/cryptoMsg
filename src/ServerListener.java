import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

/**
 * Created by theovac on 11/17/16.
 */
public class ServerListener implements Runnable {
    Socket clientSocket;
    ServerSocket server;
    Boolean serverConStatus = false;
    Boolean isAlive = true;
    public ServerListener(ServerSocket server) {
        this.server = server;
    }
    public void run() {
        try {
            clientSocket = server.accept();
            serverConStatus = true;
            System.out.println("Connected to client.");
        } catch (IOException e) {
            System.out.println(e);
        }
    }


}
