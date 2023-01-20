package proxy.server.messages;

import lombok.extern.java.Log;
import proxy.server.utils.SocksConst;

import java.nio.ByteBuffer;

@Log
public class ServerReplyMsg extends SocksMsg{

    private final byte reply;
    private final byte[] addr;
    private final int port;
    private final boolean isIpv4;

    ServerReplyMsg(SocksMsg.REPLY reply, boolean isIpv4, byte[] addr, int port){
        if(addr == null || (isIpv4 && addr.length != 4) || addr.length < 3 || port < 0 || port > 65535){
            log.severe("Сервер создал невалидный ответ.");
            if(addr != null){
                log.severe(" addr len: "+addr.length + " port " + port);
            } else {
                log.severe(" addr len: NULL " + " port " + port);
            }
            type = MSG_TYPE.BAD_FORMAT;
            this.reply = 0;
            this.addr = null;
            this.port = 0;
            this.isIpv4 = false;
            return;
        }
        this.reply = reply.toByte();
        this.addr = addr;
        this.port = port;
        this.isIpv4 = isIpv4;
        type = MSG_TYPE.SERVER_REPLY;
    }

    @Override
    public ByteBuffer toByteBuffer(){
        if(type == MSG_TYPE.SERVER_REPLY){
            ByteBuffer byteBuffer = ByteBuffer.allocate(6 + addr.length);
            byteBuffer.put(SocksConst.SOCKS_VERSION);
            byteBuffer.put(reply);
            byteBuffer.put((byte)0x00);
            if(isIpv4){
                byteBuffer.put((byte)0x01);
            } else {
                byteBuffer.put((byte)0x03);
            }
            byteBuffer.put(addr);
            byteBuffer.put((byte)((port >> 8) & 0xff));
            byteBuffer.put((byte)((port) & 0xff));
            byteBuffer.flip();
            return byteBuffer.asReadOnlyBuffer();
        }

        throw new RuntimeException("function call to invalid msg, ServerReplyMsg, toByteBuffer");
    }
}
