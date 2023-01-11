package com.grishaprimilabu.network;

import com.grishaprimilabu.model.entities.Config;

public class NetworkConfig {
    public static final int ANNOUNCEMENT_DELAY_MLS = 1000;
    public static final int MAX_PACKET_SIZE_BYTES = 4 * 1024;
    public static final int UNICAST_SOCKET_TIMEOUT = 150;
    public static final int MULTICAST_SOCKET_TIMEOUT = 250;
    public static final String MULTICAST_ADDR_STR = "239.192.0.4";
    public static final int MULTICAST_PORT = 9192;

    public static long PING_TIME_MLS = Config.getInstance().getStateDelayMs() / 10;
    public static long TIMEOUT_TIME_MLS = Config.getInstance().getStateDelayMs() * 8 / 10;
}
