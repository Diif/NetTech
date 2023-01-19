package proxy;

import lombok.extern.java.Log;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;
import proxy.server.Proxy;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Arrays;
import java.util.Set;

@Log
public class App {
    public static void main(String[] args) {
        int port;
        try {
            port = getPort(args);
        } catch (Exception e){
            log.severe(e.getLocalizedMessage());
            return;
        }

        try {
            Proxy.startProxy(port);
        } catch (IOException e) {
            log.severe(e.getLocalizedMessage() +'\n' + Arrays.toString(e.getStackTrace()));
        }
//        DatagramSocket socket = null;
//        DatagramChannel socket = null;
//        try {
//            Record qry = Record.newRecord(Name.fromString("google.com."), Type.A, DClass.IN);
//            Message message = Message.newQuery(qry);
//
//            socket = DatagramChannel.open();
//            socket.configureBlocking(false);
//            socket.bind(null);
//            byte[] data = message.toWire();
//            log.warning(String.valueOf(socket.send(ByteBuffer.wrap(data).asReadOnlyBuffer(), new InetSocketAddress(InetAddress.getByName("8.8.8.8"),53))));
//
//            ByteBuffer byteBuffer = ByteBuffer.allocate(512);
//            socket.configureBlocking(true);
//            socket.receive(byteBuffer);

//            socket = new DatagramSocket();
//            socket.connect(InetAddress.getByName("8.8.8.8"),53);
//            byte[] data = message.toWire();
//            DatagramPacket packet = new DatagramPacket(data, data.length);
//            packet.setSocketAddress(new InetSocketAddress(InetAddress.getByName("8.8.8.8"), 53));
//            socket.send(packet);

//        } catch (TextParseException e){
//            log.severe("Bad parse");
//        } catch (SocketException e) {
//            log.severe("Bad socket " + e.getLocalizedMessage());
//        } catch (UnknownHostException e) {
//            log.severe("Bad dns ip");
//        } catch (IOException e) {
//            log.severe("Can't send " + e.getLocalizedMessage());
//        }
//
//        try {
//            DatagramPacket packet = new DatagramPacket(new byte[512],512);
//            socket.receive(packet);
//            Message message1 = new Message(ByteBuffer.wrap(packet.getData()));
//            log.warning(message1.getSection(1).get(0).rdataToString());
//            log.info(message1.toString());
//            socket.close();
//        } catch (IOException e){
//            log.severe("Cat't get: " + e.getLocalizedMessage());
//        }


    }

    private static int getPort(String[] args) throws Exception{
        if (args.length < 1){
            throw new Exception("not enough args");
        }

        return Integer.parseInt(args[0]);

    }

}
