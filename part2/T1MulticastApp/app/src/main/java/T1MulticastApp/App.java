/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package T1MulticastApp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.util.*;

public class App {

    public static void main(String[] args) throws IOException, InterruptedException {
//        MulticastSocket socket = new MulticastSocket(Model.APP_PORT);
//        byte ip1 = (byte) Integer.parseInt("224");
//        byte ip2 = (byte) Integer.parseInt("1");
//        byte ip3 = (byte) Integer.parseInt("1");
//        byte ip4 = (byte) Integer.parseInt("1");
//
//        byte[] ip = new byte[4];
//        ip[0] = ip1;
//        ip[1] = ip2;
//        ip[2] = ip3;
//        ip[3] = ip4;
//
//        InetAddress address = InetAddress.getByAddress(ip);
//
//        socket.joinGroup(address);
//        DatagramPacket packet = new DatagramPacket(new byte[50], 50);
//        packet.setAddress(address);
//        packet.setPort(Model.APP_PORT);
//
//        Thread test = new Thread(() -> {
//            try {
//                System.out.println("THREAD STARTS");
//                MulticastSocket newsocket = new MulticastSocket(Model.APP_PORT);
//                newsocket.joinGroup(address);
//                DatagramPacket p = new DatagramPacket(new byte[100], 100);
//                while (true){
//                    newsocket.receive(p);
//                    System.out.println("THREAD " + new String(p.getData()));
//                }
//            } catch (IOException e) {
//                System.err.println("THReAD FALLS");
//                throw new RuntimeException(e);
//            }
//        });
//        test.start();
//
//        while (true) {
//            Thread.sleep(2000);
//            packet.setData("TEST p".getBytes());
//            socket.send(packet);
//        }

        try {
            InetAddress address = getAddressByString(args[0]);
            String name = getName(args[1]);
            Model.IP_TYPE ipType = isIpv4(args[0]) ? Model.IP_TYPE.IPV4 : Model.IP_TYPE.IPV6;
            Controller controller = new Controller();
            controller.startApp(name, address, ipType);
        } catch (Exception e){
            System.err.println("Incorrect passed args.\nError: " + e.getLocalizedMessage() + "\nPass args [IP] [NAME] with command-line");
            System.exit(1);
        }
    }

    private static InetAddress getAddressByString(String ipMaybe) throws UnknownHostException {
        if(isIpv4(ipMaybe))
        {
            String[] ipParts = ipMaybe.split("\\.");
            byte[] ip = new byte[4];
            for(int i = 0, maxParts = 4; i < maxParts; i++ ){
                ip[i] =((byte)Integer.parseInt(ipParts[i]));
            }
            return InetAddress.getByAddress(ip);
        } else if(isIpv6(ipMaybe))
        {
            String[] ipParts = ipMaybe.split(":");
            String hexIpString = String.join("",ipParts);
            HexFormat hex = HexFormat.of().withLowerCase();
            byte[] ipv6InBytes = hex.parseHex(hexIpString);
            return InetAddress.getByAddress(ipv6InBytes);
        } else
        {
            throw new RuntimeException("Incorrect passed ip.\n");
        }
    }

    private static String getName(String arg){
        if(arg.length() == 0 || arg.length() > Model.APP_USER_MAX_NAME){
            throw new RuntimeException("Invalid name.");
        }
        return arg;
    }

    private static boolean isIpv4(String ipMaybe){
        return ipMaybe.indexOf('.') != -1;
    }

    private static boolean isIpv6(String ipMaybe){
        return ipMaybe.indexOf(':') != -1;
    }
}
