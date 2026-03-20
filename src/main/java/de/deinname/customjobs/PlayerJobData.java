package de.deinname.customjobs;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class PlayerJobData {

    private final Map<String, JobProgress> progressByJob = new HashMap<>();

    
    public JobProgress getOrCreateProgress(final String jobId) {
        return progressByJob.computeIfAbsent(jobId, ignored -> new JobProgress());
    }

    
    public void setProgress(final String jobId, final JobProgress progress) {
        progressByJob.put(jobId, progress);
    }

    
    public Map<String, JobProgress> getProgressByJob() {
        return Collections.unmodifiableMap(progressByJob);
    }
}
