package protocol;

import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.extern.java.Log;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Log
public class ProtocolClientHelper implements Closeable {

    private static final int MAX_SEND_BUFFER_SIZE = 4 * 1024 * 1024;
    private final Socket socket;
    private final OutputStream outputStream;
    private FileInfo fileInfo;

    public ProtocolClientHelper(Socket clientSocket) throws IOException {
        socket = clientSocket;
        outputStream = socket.getOutputStream();
    }

    public void sendFile(String filePath){

        try {
            fileInfo = fillFileInfo(filePath);
        } catch (IOException e) {
            log.severe( Thread.currentThread().getName() + " ProtocolClientHelper: unable to get file information\n\t" + e.getLocalizedMessage() );
            throw new RuntimeException(e);
        }

        try {
            sendMetaInfo();
            log.fine( Thread.currentThread().getName() + " ProtocolClientHelper: sent metainfo." );
        } catch (IOException e) {
            log.severe( Thread.currentThread().getName() + " ProtocolClientHelper: unable to send metainfo:\n\t" + e.getLocalizedMessage() );
            throw new RuntimeException(e);
        }

        try {
            sendFileName();
            log.fine( Thread.currentThread().getName() + " ProtocolClientHelper: sent file name." );
        } catch (IOException e) {
            log.severe( Thread.currentThread().getName() + " ProtocolClientHelper: unable to send file name:\n\t" + e.getLocalizedMessage() );
            throw new RuntimeException(e);
        }

        try {
            sendFileBody(filePath);
        } catch (IOException e) {
            log.severe( Thread.currentThread().getName() + " ProtocolClientHelper: unable to send file body:\n\t" + e.getLocalizedMessage() );
            throw new RuntimeException(e);
        }
        log.fine( Thread.currentThread().getName() + " ProtocolClientHelper: file has been sent to server!" );
        close();
    }

    private FileInfo fillFileInfo(String filePath) throws IOException {
        FileInfo fileInfo = new FileInfo();
        Path file = Paths.get(filePath);

        fileInfo.setFileName(file.getFileName().toString());
        fileInfo.setFileNameSizeInBytes(fileInfo.getFileName().getBytes(StandardCharsets.UTF_8).length);
        fileInfo.setFileSizeInBytes(Files.size(file));

        return fileInfo;

    }

    synchronized private void sendMetaInfo() throws IOException {
        outputStream.write(ByteBuffer.allocate(Integer.BYTES).putInt(fileInfo.getFileNameSizeInBytes()).array());
        outputStream.write(ByteBuffer.allocate(Long.BYTES).putLong(fileInfo.getFileSizeInBytes()).array());
    }

    synchronized private void sendFileName() throws IOException {
        outputStream.write(fileInfo.getFileName().getBytes(StandardCharsets.UTF_8));
    }

    synchronized private void sendFileBody(String filaPath) throws IOException {
        long numBytesToSend = fileInfo.getFileSizeInBytes();
        @Cleanup InputStream inputStream = null;

        try {
            inputStream = Files.newInputStream(Paths.get(filaPath));
        } catch (IOException e){
            log.warning(Thread.currentThread().getName() + " ProtocolClientHelper: can't read file because of unknown reason:\n\t" + e.getLocalizedMessage());
        }

        int numBytes = 0;
        byte[] buffer = new byte[MAX_SEND_BUFFER_SIZE];
        do {
            numBytes = inputStream.read(buffer);
            if(numBytes > 0){
                outputStream.write(buffer,0,numBytes);
                numBytesToSend-=numBytes;
            }

        }while (numBytes != -1);

        if(numBytesToSend != 0){
            log.warning(Thread.currentThread().getName() + " ProtocolClientHelper: sending file body failed. Bytes that was not sent: " + numBytesToSend);
        }

    }

    @Override
    @SneakyThrows
    public void close() {
        socket.close();
        outputStream.close();
    }
}
