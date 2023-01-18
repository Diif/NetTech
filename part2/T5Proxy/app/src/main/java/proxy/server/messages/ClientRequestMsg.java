package proxy.server.messages;

import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.java.Log;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import static proxy.server.utils.SocksConst.SOCKS_VERSION;

@Getter
@Log
public class ClientRequestMsg extends SocksMsg{

    @Accessors(fluent = true)
    private boolean hasConnectCmd = false;
    @Accessors(fluent = true)
    private boolean hasIpv4 = false;
    private byte[] ip;
    @Accessors(fluent = true)
    private boolean hasDomain = false;
    private byte[] domain;
    private int port;

    ClientRequestMsg(ByteBuffer byteBuffer){
        if(byteBuffer.get(2) != 0x00){
            log.warning("Ошибка парсинга запроса: неверное зарезервированное поле");
            type = MSG_TYPE.BAD_FORMAT;
            return;
        }

        if(byteBuffer.get(1) == 0x01){
            hasConnectCmd = true;
        }
        if(byteBuffer.get(3) == 0x01){
            hasIpv4 = true;
        }else if(byteBuffer.get(3) == 0x03){
            hasDomain = true;
        } else if(byteBuffer.get(3) != 0x04){
            log.warning("Ошибка парсинга запроса: в качестве запроса вообще хрень какая-то прилетела");
            type = MSG_TYPE.BAD_FORMAT;
            return;
        }

        int portIndex = byteBuffer.position() - 2;
        if(hasDomain){
            domain = new byte[portIndex - 4];
            for (int i = 4, j = 0; i < portIndex; i++, j++){
                domain[j] = byteBuffer.get(i);
            }
        } else if (hasIpv4){
            if(portIndex - 4 != 4){
                log.warning("Ошибка парсинга запроса: некорректный размер IP поля");
                type = MSG_TYPE.BAD_FORMAT;
                return;
            }

            ip = new byte[4];
            for (int i = 4, j = 0; i < portIndex; i++, j++){
                ip[j] = byteBuffer.get(i);
            }
        } else {
            ip = new byte[portIndex - 4];
            for (int i = 4, j = 0; i < portIndex; i++, j++){
                ip[j] = byteBuffer.get(i);
            }
        }
        port = ((byteBuffer.get(portIndex) & 0xff) << 8) | (byteBuffer.get(portIndex+1) & 0xff);

        type = MSG_TYPE.CLIENT_REQUEST;

    }

    @Override
    public ByteBuffer toByteBuffer(){
        throw new RuntimeException("Useless ClientRequestMsg's toByteBuffer call");
    }

}
