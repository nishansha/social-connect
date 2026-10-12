package com.triasoft.socialpublisher.scheduler;

import com.triasoft.socialpublisher.service.SocialPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives every in-flight {@code SocialPost} through its state machine — the request thread only
 * ever does the first synchronous step (container/video-gen submission); everything after that
 * (clip polling, stitching, container polling, publish) happens here, on its own schedule, not a
 * caller's request thread.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SocialJobPoller {

    private final SocialPostService socialPostService;

    @Scheduled(fixedDelay = 15_000, initialDelay = 5_000)
    public void pollVideoGenJobs() {
        runSafely("advanceVideoGenJobs", socialPostService::advanceVideoGenJobs);
    }

    @Scheduled(fixedDelay = 15_000, initialDelay = 7_000)
    public void pollStitching() {
        runSafely("advanceStitching", socialPostService::advanceStitching);
    }

    @Scheduled(fixedDelay = 15_000, initialDelay = 9_000)
    public void pollPublishing() {
        runSafely("advancePublishing", socialPostService::advancePublishing);
    }

    private void runSafely(String name, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            // One bad post (or a transient provider outage) must not stop the poller from ever
            // running again — log and let the next tick retry.
            log.error(":: SocialJobPoller - {} failed, will retry next tick: {} ::", name, e.getMessage(), e);
        }
    }
}
