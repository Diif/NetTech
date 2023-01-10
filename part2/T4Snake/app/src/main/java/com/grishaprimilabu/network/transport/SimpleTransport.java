package com.grishaprimilabu.network.transport;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;

import static com.grishaprimilabu.network.NetworkConfig.*;

public class SimpleTransport implements TransportLayer{

    private final InetAddress GROUP_ADDR;
    private final MulticastSocket multicastSocket;
    private final DatagramSocket datagramSocket;

    public SimpleTransport() throws IOException {
        multicastSocket = new MulticastSocket(MULTICAST_PORT);
        GROUP_ADDR = InetAddress.getByName(MULTICAST_ADDR_STR);
        multicastSocket.joinGroup(GROUP_ADDR);
        multicastSocket.setSoTimeout(MULTICAST_SOCKET_TIMEOUT);
        datagramSocket = new DatagramSocket(0);
        datagramSocket.setSoTimeout(UNICAST_SOCKET_TIMEOUT);
        System.out.println(datagramSocket.getLocalSocketAddress().toString());
    }
    @Override
    public void sendMulticast(DatagramPacket packet) throws IOException {
        packet.setAddress(GROUP_ADDR);
        packet.setPort(MULTICAST_PORT);
        datagramSocket.send(packet);
    }

    @Override
    public void sendUnicast(DatagramPacket packet, InetSocketAddress address) throws IOException {
        packet.setSocketAddress(address);
        datagramSocket.send(packet);
    }

    @Override
    public void sendUnicast(DatagramPacket packet) throws IOException {
        datagramSocket.send(packet);
    }

    @Override
    public DatagramPacket receiveMulticast() throws IOException{
        DatagramPacket packet = new DatagramPacket(new byte[MAX_PACKET_SIZE_BYTES], MAX_PACKET_SIZE_BYTES);
        multicastSocket.receive(packet);
        byte[] msgData = Arrays.copyOf(packet.getData(), packet.getLength());
        packet.setData(msgData);
        return packet;
    }

    @Override
    public DatagramPacket receiveUnicast() throws IOException {
        DatagramPacket packet = new DatagramPacket(new byte[MAX_PACKET_SIZE_BYTES], MAX_PACKET_SIZE_BYTES);
        datagramSocket.receive(packet);
        byte[] msgData = Arrays.copyOf(packet.getData(), packet.getLength());
        packet.setData(msgData);
        return packet;
    }


    @Override
    public void close() {
        multicastSocket.close();
        datagramSocket.close();
    }
}
