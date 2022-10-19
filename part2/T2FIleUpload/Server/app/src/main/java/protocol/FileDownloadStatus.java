package protocol;

import lombok.*;
import lombok.experimental.Accessors;

@Accessors(fluent = true)
@Getter(onMethod_ = {@Synchronized})
@Setter(onMethod_ = {@Synchronized})
@NoArgsConstructor
public class FileDownloadStatus{
    private String fileName;
    private long startTime;
    private long endTime;
    private long printedCount;
    private long receivedBytes;
    private long bytesCountedBySpeedCounter;

    @Synchronized
    public long[] getStartEndTimesReceivedBytes(){
        return new long[]{startTime,endTime,receivedBytes};
    }

}
