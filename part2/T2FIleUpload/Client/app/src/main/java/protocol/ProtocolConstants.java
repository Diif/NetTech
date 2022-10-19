package protocol;

import lombok.Getter;
import lombok.experimental.Accessors;


@Accessors(fluent = true)
public class ProtocolConstants {
    @Getter
    static final private int MAX_FILE_NAME_SIZE_IN_BYTES = 4096;
    @Getter
    static final private long MAX_FILE_SIZE_IN_BYTES = 1024L *1024*1024*1024;
}
