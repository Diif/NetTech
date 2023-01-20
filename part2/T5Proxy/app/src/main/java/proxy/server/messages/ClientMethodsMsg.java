package proxy.server.messages;

import java.nio.ByteBuffer;

import static proxy.server.utils.SocksConst.SOCKS_VERSION;

public class ClientMethodsMsg extends SocksMsg{

    private boolean hasNoAuth = false;

    public boolean hasNoAuth(){
        return hasNoAuth;
    }

    ClientMethodsMsg(ByteBuffer byteBuffer){
        byte numMethods = byteBuffer.get(1);
        if(numMethods < 1 || numMethods < byteBuffer.position() - 2){
            type = MSG_TYPE.BAD_FORMAT;
            return;
        }

        type = MSG_TYPE.CLIENT_METHODS;

        for (int i = 2; i < numMethods + 2; i++){
            if((byte) 0x00 == byteBuffer.get(i)){
                hasNoAuth = true;
                break;
            }
        }

    }

    @Override
    public ByteBuffer toByteBuffer(){
        throw new RuntimeException("Useless ClientMethodsMsg's toByteBuffer call");
    }

}