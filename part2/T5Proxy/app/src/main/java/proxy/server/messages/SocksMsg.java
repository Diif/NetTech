package proxy.server.messages;

import lombok.extern.java.Log;

import java.nio.ByteBuffer;

import static proxy.server.utils.SocksConst.*;
@Log
public class SocksMsg {

    public enum MSG_TYPE{
        CLIENT_METHODS, SERVER_METHOD_CHOICE, CLIENT_REQUEST, SERVER_REPLY, BAD_FORMAT, INVALID_VERSION
    }

    public enum MSG_CLIENT_TYPE {
        CLIENT_METHODS, CLIENT_REQUEST
    }

    public enum MSG_SERVER_TYPE {
        SERVER_METHOD_CHOICE,  SERVER_REPLY
    }

    public enum AUTH_METHODS {
        NO_AUTH, NOT_SUPPORTED;

        public byte toByte(){
            switch (this){
                case NO_AUTH -> {return 0x00;}
                case NOT_SUPPORTED -> {return (byte) 0xff;}
            }
            throw new RuntimeException("unreachable code, AUTH_METHOD toByte");
        }

    }

    public enum REPLY {
        SUCCEEDED, COMMAND_NOT_SUPPORTED, ADDRESS_NOT_SUPPORTED, HOST_UNREACHABLE, GENERAL_ERROR;

        public byte toByte(){
            switch (this){
                case SUCCEEDED -> {return 0x00;}
                case GENERAL_ERROR -> {return 0x01;}
                case HOST_UNREACHABLE -> {return 0x04;}
                case COMMAND_NOT_SUPPORTED -> {return 0x07;}
                case ADDRESS_NOT_SUPPORTED -> {return 0x08;}
            }
            throw new RuntimeException("unreachable code, REPLY toByte");
        }
    }

    protected MSG_TYPE type;
    protected SocksMsg(){}

    private SocksMsg(MSG_TYPE type){
        this.type = type;
    }
    public boolean isInvalid(){
        return type == MSG_TYPE.BAD_FORMAT || type == MSG_TYPE.INVALID_VERSION;
    }

    public ClientMethodsMsg getClientMethodsMsg(){
        if(type == MSG_TYPE.CLIENT_METHODS){
            return (ClientMethodsMsg) this;
        } else {
            throw new RuntimeException("Bad cast to ClientMethodsMsg");
        }
    }

    public ClientRequestMsg getClientRequestMsg(){
        if(type == MSG_TYPE.CLIENT_REQUEST){
            return (ClientRequestMsg) this;
        } else {
            throw new RuntimeException("Bad cast to ClientRequestMsg");
        }
    }

    public ByteBuffer toByteBuffer(){
        throw new RuntimeException("Msg can't be cast to byteBuffer");
    }


    public static SocksMsg parseFrom(MSG_CLIENT_TYPE type, ByteBuffer data){

        if(data.position() < 3 || data.limit() < 3){
            log.warning("Ошибка парсинга сообщения: bad format");
            return new SocksMsg(MSG_TYPE.BAD_FORMAT);
        }

        if(data.get(0) != SOCKS_VERSION){
            log.warning("Ошибка парсинга сообщения: invalid version");
            return new SocksMsg(MSG_TYPE.INVALID_VERSION);
        }

        switch (type){
            case CLIENT_METHODS -> {return new ClientMethodsMsg(data);}
            case CLIENT_REQUEST -> {return new ClientRequestMsg(data);}
        }

        throw new RuntimeException("unreached code, parseFrom client msg");

    }

    public static ServerMethodChoiceMsg createServerMethodChoice(AUTH_METHODS method){
        return new ServerMethodChoiceMsg(method);
    }

    public static ServerReplyMsg createServerReply(REPLY reply,boolean isIpv4 ,byte[] addr, int port){
        return new ServerReplyMsg(reply,isIpv4, addr, port);
    }

}
