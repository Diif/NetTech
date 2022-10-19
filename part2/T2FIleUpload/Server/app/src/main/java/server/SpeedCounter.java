package server;

import lombok.extern.java.Log;
import protocol.FileDownloadStatus;
import protocol.ProtocolServerHelper;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

@Log
public class SpeedCounter {
    private static final int TIME_INTERVAL = 3;
    private final Set<FileDownloadStatus> aliveDownloads;
    private final Stack<FileDownloadStatus> candidatesToRemove;

    public SpeedCounter() {
        aliveDownloads = Collections.synchronizedSet(new HashSet<>());
        candidatesToRemove = new Stack<>();
    }

    public void PrintAndUpdateSpeedInfo(){
        if(aliveDownloads.isEmpty()){
            return;
        }
        StringBuilder builder = new StringBuilder();
        try {
            for (FileDownloadStatus status : aliveDownloads) {
                long[] stats = status.getStartEndTimesReceivedBytes();
                long startTime = stats[0];
                long endTime = stats[1];
                long recBytes = stats[2];

                if (startTime != 0) {
                    long time = Instant.now().toEpochMilli();

                    if (time - startTime < TIME_INTERVAL * 1000) {
                        continue;
                    }


                    builder.append("File ");
                    builder.append(status.fileName());
                    builder.append(": ");

                    if (startTime == endTime) {
                        builder.append("speed is too high to be calculated.");
                        addRemoveCandidate(status);
                        continue;
                    }

                    //if ended but not counted
                    if (endTime != 0 && status.printedCount() == 0) {
                        builder.append(((double) recBytes / (endTime - startTime)) * 1000);
                    }
                    //otherwise
                    else {
                        builder.append(((double) recBytes - status.bytesCountedBySpeedCounter()) / TIME_INTERVAL);
                    }
                    builder.append(" Bytes/S (cur), ");


                    if (endTime != 0) {
                        builder.append((((double) recBytes - status.bytesCountedBySpeedCounter()) / (endTime - startTime)) * 1000);
                    } else if (status.printedCount() != 0) {
                        builder.append(((double) recBytes - status.bytesCountedBySpeedCounter()) / (status.printedCount() * TIME_INTERVAL));
                    } else {
                        builder.append(((double) recBytes - status.bytesCountedBySpeedCounter()) / (TIME_INTERVAL));
                    }
                    builder.append(" Bytes/S (avg).\n");

                    if (endTime != 0) {
                        addRemoveCandidate(status);
                    } else {
                        status.printedCount(status.printedCount() + 1);
                        status.bytesCountedBySpeedCounter(recBytes);
                    }
                }
            }
        } catch (Exception e){
            log.severe(Thread.currentThread().getName() +" SpeedCounter: unknown exception. Speed module is off.");
            e.printStackTrace();
            throw e;
        }
        if(builder.toString().equals("")){
            return;
        }

        removeCandidates();
        log.info(Thread.currentThread().getName() +"\nSpeedCounter: \n" + builder);

    }
    public void addClient(ProtocolServerHelper helper){
        aliveDownloads.add(helper.getFileDownloadStatus());
    }

    public void removeClient(ProtocolServerHelper helper){
        aliveDownloads.remove(helper.getFileDownloadStatus());
    }

    private void addRemoveCandidate(FileDownloadStatus status){
        candidatesToRemove.push(status);
    }

    private void removeCandidates(){
        for(FileDownloadStatus status : candidatesToRemove){
            removeClient(status);
        }
        candidatesToRemove.clear();
    }

    private void removeClient(FileDownloadStatus status){
        aliveDownloads.remove(status);
    }

}
