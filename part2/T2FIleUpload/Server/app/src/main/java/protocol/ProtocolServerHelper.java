package protocol;

import lombok.Builder;
import lombok.Cleanup;
import lombok.Getter;
import lombok.extern.java.Log;
import server.SpeedCounter;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Time;
import java.time.Instant;


//PROTOCOL FORMAT
//    ---------------------------------------------------
//    |fileNameSizeInBytes|fileSizeInBytes|filename|file|
//    ---------------------------------------------------

@Log
public class ProtocolServerHelper implements Closeable {

    static final private int MAX_DOWNLOAD_BUFFER_SIZE = 4 * 1024 * 1024;
    private boolean hasMetaInfo;
    private final InputStream inputStream;

    private final FileInfo fileInfo;

    @Getter
    private final FileDownloadStatus fileDownloadStatus;

    public ProtocolServerHelper(Socket clientSocket) throws IOException {
        inputStream = clientSocket.getInputStream();
        hasMetaInfo = false;
        fileInfo = new FileInfo();
        fileDownloadStatus = new FileDownloadStatus().startTime(0).endTime(0).printedCount(0);
    }

    public void downloadFile() throws Exception {
        receiveMetaInfoFromClient();

        log.fine(Thread.currentThread().getName() + " ProtocolServerHelper: Got metainfo: " + fileInfo.getFileNameSizeInBytes() + "(name size) " + fileInfo.getFileSizeInBytes() + "(filesize)");
        receiveFileName();
        log.fine(Thread.currentThread().getName() + " ProtocolServerHelper: Got file name: " + fileInfo.getFileName());
        checkFileName();
        log.fine(Thread.currentThread().getName() + " ProtocolServerHelper: file name: " + fileInfo.getFileName() + " is ok. Downloading...");
        receiveAndWriteFileBody();
        log.fine(Thread.currentThread().getName() + " ProtocolServerHelper: downloaded: " + fileInfo.getFileName());

    }

    private void receiveAndWriteFileBody() throws Exception{
        File file = createFile();
        @Cleanup FileOutputStream outputStream = new FileOutputStream(file);

        long bytesToRead = fileInfo.getFileSizeInBytes();
        byte[] buffer = new byte[MAX_DOWNLOAD_BUFFER_SIZE];
        int numBytes = 0;

        fileDownloadStatus.fileName(fileInfo.getFileName())
                .bytesCountedBySpeedCounter(0)
                .receivedBytes(0)
                .startTime(Instant.now().toEpochMilli());

        do{
            numBytes = inputStream.read(buffer);
            if(numBytes > 0){
                outputStream.write(buffer, 0,numBytes);
                fileDownloadStatus.receivedBytes(fileDownloadStatus.receivedBytes() + numBytes);
                bytesToRead-=numBytes;
            }

            if(bytesToRead < 0){
                fileDownloadStatus.endTime(Instant.now().toEpochMilli());
                log.severe( Thread.currentThread().getName() + " ProtocolServerHelper: file size mismatch. Too many bytes.");
                throw new IOException("File mismatch.");
            }

        }
        while (numBytes != -1);

        fileDownloadStatus.endTime(Instant.now().toEpochMilli());
        if(bytesToRead > 0){
            log.severe( Thread.currentThread().getName() + " ProtocolServerHelper: file size mismatch. Expect additional bytes: " + bytesToRead);
            throw new IOException("File mismatch.");
        }
    }

    private File createFile() throws IOException {

        if(!hasMetaInfo){
            log.severe( Thread.currentThread().getName() + " ProtocolServerHelper: trying to create file name without metainfo.");
            throw new IOException("Can't create file without metainfo.");
        }

        Files.createDirectories(Paths.get("uploads"));

        String newFileName = "uploads/" + fileInfo.getFileName();

        boolean fileCreated = false;
        Path filePath = Paths.get(newFileName);
        File file = null;

        //to avoid data race
        int i = 0;
        while (!fileCreated){
            try {
                //atomic check and create
               file =  Files.createFile(filePath).toFile();
               fileCreated = true;
            } catch (FileAlreadyExistsException ignored){
                filePath = Paths.get(newFileName + i);
                i++;
            } catch (IOException e){
                log.severe( Thread.currentThread().getName() + " ProtocolServerHelper: can't create file:\n\t" + e.getMessage());
                throw e;
            }
        }

        return file;

    }
    private void checkFileName() throws Exception {
        if(fileInfo.getFileName().matches(".*/\\.\\./.*") || fileInfo.getFileName().matches(".*/\\./.*")){
            throw new Exception("Invalid filename.");
        }
    }

    private void receiveFileName() throws IOException {
        if(!hasMetaInfo){
            log.info( Thread.currentThread().getName() + " ProtocolServerHelper: trying to get file name without metainfo.");
            return;
        }

        int totalBytes = fileInfo.getFileNameSizeInBytes();
        int bytesToRead = totalBytes;
        byte[] buffer = new byte[bytesToRead];

        while (bytesToRead != 0) {
            int numBytes = inputStream.read(buffer,totalBytes - bytesToRead,bytesToRead);
            if(numBytes == -1){
                log.severe( Thread.currentThread().getName() + " ProtocolServerHelper: invalid number of read bytes (filename) - " + numBytes);
                throw new IOException("Can't get filename from client");
            } else {
                bytesToRead-=numBytes;
            }
        }

        fileInfo.setFileName(new String(buffer, StandardCharsets.UTF_8));

    }


    private void receiveMetaInfoFromClient() throws Exception {
        if(hasMetaInfo){
            log.info( Thread.currentThread().getName() + " ProtocolServerHelper: trying to get MetaInfo twice.");
            return;
        }

        int totalBytes = Integer.BYTES + Long.BYTES;
        int bytesToRead = Integer.BYTES + Long.BYTES;
        byte[] buffer = new byte[bytesToRead];

        while (bytesToRead != 0) {
            int numBytes = inputStream.read(buffer,totalBytes - bytesToRead,bytesToRead);
            if(numBytes == -1){
                log.severe( Thread.currentThread().getName() + " ProtocolServerHelper: invalid number of read bytes (metainfo) - " + numBytes);
                throw new IOException("Can't get metainfo from client");
            } else {
                bytesToRead-=numBytes;
            }
        }

        ByteBuffer wrapped = ByteBuffer.wrap(buffer);

        int fileNameSizeInBytes = wrapped.getInt();
        long fileSizeInBytes = wrapped.getLong();

        if(fileNameSizeInBytes > ProtocolConstants.MAX_FILE_NAME_SIZE_IN_BYTES() || fileNameSizeInBytes < 0){
            log.severe( Thread.currentThread().getName() + " ProtocolServerHelper: invalid fileNameSizeInBytes - " + fileNameSizeInBytes);
            throw new Exception("Incorrect file size.");
        }

        if(fileSizeInBytes > ProtocolConstants.MAX_FILE_SIZE_IN_BYTES() || fileSizeInBytes < 0){
            log.warning( Thread.currentThread().getName() + " ProtocolServerHelper: invalid fileSizeInBytes - " + fileSizeInBytes);
            throw new Exception("Incorrect file size. Max");
        }

        fileInfo.setFileSizeInBytes(fileSizeInBytes);
        fileInfo.setFileNameSizeInBytes(fileNameSizeInBytes);

        hasMetaInfo = true;

    }

    @Override
    public void close() throws IOException {
        inputStream.close();
    }
}
