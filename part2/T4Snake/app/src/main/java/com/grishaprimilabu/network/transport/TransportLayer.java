package com.grishaprimilabu.network.transport;

import com.grishaprimilabu.proto.SnakesProto;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public interface TransportLayer extends Closeable {

    DatagramPacket receiveMulticast() throws IOException;

    DatagramPacket receiveUnicast() throws IOException;
    void sendMulticast(DatagramPacket packet) throws IOException;
    void sendUnicast(DatagramPacket packet, InetSocketAddress address) throws IOException;
    void sendUnicast(DatagramPacket packet) throws IOException;
    void close();

}
