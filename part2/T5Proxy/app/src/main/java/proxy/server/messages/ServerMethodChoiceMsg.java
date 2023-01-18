package proxy.server.messages;

import proxy.server.utils.SocksConst;

import java.nio.ByteBuffer;

public class ServerMethodChoiceMsg extends SocksMsg{

    private final int MSG_SIZE_BYTES = 2;
    private final byte method;

    ServerMethodChoiceMsg(AUTH_METHODS method){
        this.method = method.toByte();
        type = MSG_TYPE.SERVER_METHOD_CHOICE;
    }

    @Override
    public ByteBuffer toByteBuffer(){
        if(type == MSG_TYPE.SERVER_METHOD_CHOICE){
            ByteBuffer byteBuffer = ByteBuffer.allocate(MSG_SIZE_BYTES);
            byteBuffer.put(SocksConst.SOCKS_VERSION);
            byteBuffer.put(method);
            byteBuffer.flip();
            return byteBuffer.asReadOnlyBuffer();
        }

        throw new RuntimeException("Unreached code, toByteBuffer (server method choice msg)");
    }
}
