package T1MulticastApp;

import javafx.application.Platform;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.channels.ClosedByInterruptException;
import java.util.Arrays;
import java.util.HexFormat;

public class Controller {
    private final MulticastSocket socket;
    private Thread listeningThread;
    private InetAddress groupAddress;
    private final DatagramPacket packet;
    private View view;

    public Controller() throws IOException {
        socket = new MulticastSocket(Model.APP_PORT);
        packet = new DatagramPacket(new byte[Model.APP_JOIN_KEY.length() + 1 + Model.APP_USER_MAX_NAME + 1],
                Model.APP_JOIN_KEY.length()+ 1 + Model.APP_USER_MAX_NAME + 1);
        groupAddress = null;
    }

    public void startApp(String userName, InetAddress groupAddress, Model.IP_TYPE ipType){
        Model.setUserName(userName);
        Model.setIpType(ipType);
        this.view = new View();
        packet.setPort(Model.APP_PORT);
        packet.setAddress(groupAddress);
        setGroup(groupAddress);
        view.GUIStart();
    }

    public void setGroup(InetAddress address){
        if(null != groupAddress){
            return;
        }

        try {
            socket.joinGroup(address);
            groupAddress = address;
            listeningThread = createListeningThread();
            listeningThread.start();
            sendJoinDatagram();
        } catch (IOException e){
            System.err.println("Can't join group with address: " + Arrays.toString(groupAddress.getAddress()));
        }
    }

    public void leaveGroup(){
        if(null != groupAddress){
            try {
                listeningThread.interrupt();
                sendExitDatagram();
                socket.leaveGroup(groupAddress);
                socket.close();
            } catch (IOException e){
                System.err.println("Can't leave group with address: " + Arrays.toString(groupAddress.getAddress()));
            }
        }
    }

    private Thread createListeningThread(){
        return new Thread(() -> {
            System.out.println("Thread starts.");
            while (true) {
                try {
                    socket.receive(packet);
                    System.out.println("Thread received packet...");
                    String data = new String(packet.getData());
                    System.out.println(data);
                    String userName, ip = null;
                    Model.DATAGRAM_TYPE packetType;
                    try {
                        packetType = checkAndGetDatagramType(data);
                        switch (Model.getIpType()) {
                            case IPV4 -> ip = convertByteArrayToIpv4String(packet.getAddress().getAddress());
                            case IPV6 -> ip = convertByteArrayToIpv6String(packet.getAddress().getAddress());
                        }
                        switch (packetType){
                            case JOIN -> {
                                userName = getNameFromDatagramData(data);
                                Model.addUser(ip, userName);
                                System.out.println("Thread add user with IP: " + ip + " and name + " + userName);
                            }
                            case EXIT -> Model.removeUser(ip);
                        }
                    } catch (Exception e){
                        System.out.println(e.getLocalizedMessage());
                        continue;
                    }
                    Platform.runLater(view::updateView);
                } catch (ClosedByInterruptException e){
                    return;
                } catch (IOException e){
                    System.err.println("Strange error. Ignore.");
                }
            }
        });
    }

    private Model.DATAGRAM_TYPE checkAndGetDatagramType(String data){
        if (data.matches("^"+ Model.APP_JOIN_KEY + ".*")){
            return Model.DATAGRAM_TYPE.JOIN;
        } if (data.matches("^"+ Model.APP_EXIT_KEY + ".*")){
            return Model.DATAGRAM_TYPE.EXIT;
        } if (data.matches("^"+ Model.APP_REPEAT_KEY + ".*")) {
            return Model.DATAGRAM_TYPE.REPEAT;
        }
        throw new RuntimeException("Invalid datagram.");
    }
    private String getNameFromDatagramData(String data){
        return data.substring(data.indexOf(' ')+1);
    }

    private void sendExitDatagram() throws IOException {
        packet.setData(Model.APP_EXIT_KEY.getBytes());
        socket.send(packet);
    }

    private void sendJoinDatagram() throws IOException {
        packet.setData((Model.APP_JOIN_KEY + " " + Model.getUserName()).getBytes());
        socket.send(packet);
    }

    private String convertByteArrayToIpv4String(byte[] array){
        byte[] ipBytes =  packet.getAddress().getAddress();
        int[] ipInt = new int[ipBytes.length];
        for(int i = 0; i < ipBytes.length; ipInt[i] = ipBytes[i++] & 0xff);
        return Arrays
                .stream(ipInt)
                .mapToObj(String::valueOf)
                .reduce((a, b) -> a.concat(".").concat(b))
                .get();
    }

    private String convertByteArrayToIpv6String(byte[] array){
        return HexFormat.of().formatHex(array);
    }

}
