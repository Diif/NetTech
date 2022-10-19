import lombok.extern.java.Log;
import protocol.ProtocolClientHelper;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;


@Log
public class Client {
    private final Socket socket;

    public Client(InetAddress address, int port) throws IOException {
        socket = new Socket(address, port);
    }

    public void sendFile(String filepath) throws IOException {
        ProtocolClientHelper helper = new ProtocolClientHelper(socket);
        helper.sendFile(filepath);

    }

}
