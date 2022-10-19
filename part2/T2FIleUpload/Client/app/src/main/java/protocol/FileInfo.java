package protocol;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class FileInfo {

    private long fileSizeInBytes;
    private int fileNameSizeInBytes;
    private String fileName;


    public FileInfo(){
        fileName = null;
        fileSizeInBytes = -1;
        fileNameSizeInBytes = -1;
    }
}
