package com.triasoft.socialpublisher.service;

import com.triasoft.socialpublisher.client.SocialPublishClient;
import com.triasoft.socialpublisher.client.VideoGenClient;
import com.triasoft.socialpublisher.dto.SocialPostRs;
import com.triasoft.socialpublisher.dto.SocialPostSubmitRq;
import com.triasoft.socialpublisher.entity.SocialAccountConfig;
import com.triasoft.socialpublisher.entity.SocialClient;
import com.triasoft.socialpublisher.entity.SocialPost;
import com.triasoft.socialpublisher.entity.SocialPostClip;
import com.triasoft.socialpublisher.enums.SocialClipStatus;
import com.triasoft.socialpublisher.enums.SocialFailureStage;
import com.triasoft.socialpublisher.enums.SocialPostStatus;
import com.triasoft.socialpublisher.enums.SocialPlatform;
import com.triasoft.socialpublisher.enums.SocialPostType;
import com.triasoft.socialpublisher.exception.ApiException;
import com.triasoft.socialpublisher.repository.SocialAccountConfigRepository;
import com.triasoft.socialpublisher.repository.SocialClientRepository;
import com.triasoft.socialpublisher.repository.SocialPostClipRepository;
import com.triasoft.socialpublisher.repository.SocialPostRepository;
import com.triasoft.socialpublisher.service.impl.KenBurnsVideoGenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Core orchestrator — the state machine described in the plan:
 * {@code PENDING_VIDEO_GEN -> STITCHING -> VIDEO_READY_FOR_REVIEW -> PUBLISHING -> PUBLISHED|FAILED}
 * (PHOTO posts skip straight to PUBLISHING). Request-time methods ({@link #submitPhoto},
 * {@link #submitVideo}, {@link #approve}) run inside the caller's request; {@code advance*}
 * methods are driven by {@code SocialJobPoller} on its own schedule, not the request thread.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialPostService {

    private static final int MAX_CONTAINER_POLL_ATTEMPTS = 10;
    private static final int VIDEO_PRESIGNED_URL_TTL_SECONDS = 15 * 60;
    private static final int MAX_CAROUSEL_ITEMS = 10;
    private static final String KEN_BURNS_PROVIDER = "ffmpeg-kenburns";
    private static final int MAX_KEN_BURNS_ATTEMPTS = 3;
    private static final int MAX_VIDEO_GEN_POLL_ATTEMPTS = 20; // ~5 min at the poller's 15s tick

    private final SocialPostRepository postRepository;
    private final SocialPostClipRepository clipRepository;
    private final SocialAccountConfigRepository accountConfigRepository;
    private final SocialClientRepository clientRepository;
    private final SocialPublishClient socialPublishClient;
    private final VideoGenClient videoGenClient;
    private final VideoStitchService videoStitchService;
    private final ObjectStorageService objectStorageService;
    private final WebhookDispatcher webhookDispatcher;
    private final KenBurnsVideoGenService kenBurnsVideoGenService;

    @Transactional
    public SocialPostRs submitPhoto(SocialClient client, SocialPostSubmitRq request) {
        if (request.getPostType() != SocialPostType.PHOTO) {
            throw ApiException.badRequest("postType must be PHOTO");
        }
        if (request.getPhotoUrls().size() > MAX_CAROUSEL_ITEMS) {
            throw ApiException.badRequest("Instagram supports at most " + MAX_CAROUSEL_ITEMS + " images in one post");
        }
        SocialAccountConfig account = loadActiveAccount(client, request);

        SocialPost post = new SocialPost();
        post.setSocialClientId(client.getId());
        post.setAccountRef(request.getAccountRef());
        post.setInventoryRef(request.getInventoryRef());
        post.setPlatform(request.getPlatform());
        post.setPostType(SocialPostType.PHOTO);
        post.setCaption(request.getCaption());
        post.setCallbackUrl(request.getCallbackUrl());
        post.setSourcePhotoUrls(request.getPhotoUrls());
        post.setStatus(SocialPostStatus.PUBLISHING);

        String containerId = socialPublishClient.createPhotoContainer(account, post.getSourcePhotoUrls(), request.getCaption());
        post.setMediaContainerId(containerId);

        return SocialPostRs.from(postRepository.save(post));
    }

    @Transactional
    public SocialPostRs submitVideo(SocialClient client, SocialPostSubmitRq request) {
        if (request.getPostType() != SocialPostType.VIDEO) {
            throw ApiException.badRequest("postType must be VIDEO");
        }
        loadActiveAccount(client, request); // fail fast if no account configured, before spending on video-gen

        // Decided once, from the first photo, and applied to every clip in this post — not
        // per-photo. FfmpegVideoStitchService concatenates clips with a stream-copy (no re-encode),
        // which requires every clip to share one resolution; mixing orientations per-clip would
        // break stitching. This also sidesteps asking the model to crop/stretch a landscape photo
        // into a portrait canvas (or vice versa) — the canvas always matches the source's own shape.
        // Persisted on the post (not just a local variable) because advanceClip needs it again
        // later, off the request thread, for the Ken Burns branch below.
        VideoGenClient.VideoOrientation orientation = detectOrientation(request.getPhotoUrls().get(0));

        SocialPost post = new SocialPost();
        post.setSocialClientId(client.getId());
        post.setAccountRef(request.getAccountRef());
        post.setInventoryRef(request.getInventoryRef());
        post.setPlatform(request.getPlatform());
        post.setPostType(SocialPostType.VIDEO);
        post.setPrompt(request.getPrompt());
        post.setCaption(request.getCaption());
        post.setCallbackUrl(request.getCallbackUrl());
        post.setSourcePhotoUrls(request.getPhotoUrls());
        post.setStatus(SocialPostStatus.PENDING_VIDEO_GEN);
        post.setOrientation(orientation);
        post = postRepository.save(post);

        // No prompt means AI generation is disabled client-side (see
        // AppConfigEnum.SOCIAL_AI_VIDEO_GENERATION_ENABLED) — fall back to the free, local ffmpeg
        // Ken Burns stand-in instead of calling the paid provider. The clip is left PENDING here;
        // actual rendering happens on the poller's own tick (advanceClip), not this request thread
        // — running ffmpeg (download + encode + upload) per photo here could hold this transaction
        // open for ~50s on a full MAX_CAROUSEL_ITEMS carousel.
        boolean useAiGeneration = request.getPrompt() != null && !request.getPrompt().isBlank();

        for (int i = 0; i < request.getPhotoUrls().size(); i++) {
            String photoUrl = request.getPhotoUrls().get(i);
            SocialPostClip clip = new SocialPostClip();
            clip.setSocialPostId(post.getId());
            clip.setSourcePhotoUrl(photoUrl);
            clip.setSequenceOrder(i);
            if (useAiGeneration) {
                clip.setVideoGenProvider("replicate");
                String jobId = videoGenClient.submit(photoUrl, request.getPrompt(), orientation);
                clip.setVideoGenJobId(jobId);
                clip.setStatus(SocialClipStatus.RUNNING);
            } else {
                clip.setVideoGenProvider(KEN_BURNS_PROVIDER);
                clip.setStatus(SocialClipStatus.PENDING);
            }
            clipRepository.save(clip);
        }

        return SocialPostRs.from(post);
    }

    /** Falls back to LANDSCAPE (the common case for vehicle photos) if the image can't be read for any reason — never block a real submission over a dimension-probe hiccup. */
    private VideoGenClient.VideoOrientation detectOrientation(String photoUrl) {
        try {
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(java.net.URI.create(photoUrl).toURL());
            if (image == null) {
                throw new IllegalStateException("ImageIO could not decode an image at this URL");
            }
            return image.getHeight() > image.getWidth()
                    ? VideoGenClient.VideoOrientation.PORTRAIT
                    : VideoGenClient.VideoOrientation.LANDSCAPE;
        } catch (Exception e) {
            log.warn(":: SocialPostService - could not read image dimensions for {}, defaulting to LANDSCAPE: {} ::", photoUrl, e.getMessage());
            return VideoGenClient.VideoOrientation.LANDSCAPE;
        }
    }

    @Transactional
    public SocialPostRs approve(SocialClient client, Long postId) {
        SocialPost post = loadOwnedPost(client, postId);
        if (post.getStatus() != SocialPostStatus.VIDEO_READY_FOR_REVIEW) {
            throw ApiException.conflict("Post is not awaiting approval (status=" + post.getStatus() + ")");
        }
        SocialAccountConfig account = requireActiveAccount(client, post.getAccountRef(), post.getPlatform());

        String videoUrl = objectStorageService.presignedUrl(post.getGeneratedVideoObjectKey(), VIDEO_PRESIGNED_URL_TTL_SECONDS);
        String containerId = socialPublishClient.createVideoContainer(account, videoUrl, post.getCaption());
        post.setMediaContainerId(containerId);
        post.setApprovedAt(Instant.now());
        post.setStatus(SocialPostStatus.PUBLISHING);

        SocialPostRs result = SocialPostRs.from(postRepository.save(post));
        dispatchWebhook(client, post, result);
        return result;
    }

    public SocialPostRs getStatus(SocialClient client, Long postId) {
        SocialPost post = loadOwnedPost(client, postId);
        return enrichWithPreviewUrl(post);
    }

    /** Advances every VIDEO post still generating clips. Called by the poller, not a request thread. */
    @Transactional
    public void advanceVideoGenJobs() {
        for (SocialPost post : postRepository.findByStatusIn(List.of(SocialPostStatus.PENDING_VIDEO_GEN))) {
            List<SocialPostClip> clips = clipRepository.findBySocialPostIdOrderBySequenceOrder(post.getId());
            boolean anyFailed = false;
            boolean allDone = true;
            for (SocialPostClip clip : clips) {
                if (clip.getStatus() == SocialClipStatus.SUCCEEDED || clip.getStatus() == SocialClipStatus.FAILED) {
                    anyFailed |= clip.getStatus() == SocialClipStatus.FAILED;
                    continue;
                }
                advanceClip(clip, post.getOrientation());
                anyFailed |= clip.getStatus() == SocialClipStatus.FAILED;
                allDone &= clip.getStatus() == SocialClipStatus.SUCCEEDED || clip.getStatus() == SocialClipStatus.FAILED;
            }
            if (anyFailed) {
                failPost(post, SocialFailureStage.VIDEO_GEN, "VIDEO_GEN_FAILED", "One or more source clips failed to generate");
            } else if (allDone) {
                post.setStatus(SocialPostStatus.STITCHING);
                postRepository.save(post);
            }
        }
    }

    private void advanceClip(SocialPostClip clip, VideoGenClient.VideoOrientation orientation) {
        clip.setPollAttempts(clip.getPollAttempts() + 1);

        if (KEN_BURNS_PROVIDER.equals(clip.getVideoGenProvider())) {
            // Synchronous and local — there's no legitimate "still generating" state to wait out
            // (unlike an async remote job), so a low attempt cap: a photo URL that fails to
            // download once (expired signature, deleted object) will fail identically forever.
            // A couple of retries only covers a transient network blip.
            try {
                String objectKey = kenBurnsVideoGenService.generate(clip.getSourcePhotoUrl(), orientation);
                clip.setClipObjectKey(objectKey);
                clip.setStatus(SocialClipStatus.SUCCEEDED);
            } catch (Exception e) {
                failClip(clip, "Ken Burns generation", e, MAX_KEN_BURNS_ATTEMPTS);
            }
            clipRepository.save(clip);
            return;
        }

        try {
            VideoGenClient.VideoGenStatus status = videoGenClient.poll(clip.getVideoGenJobId());
            switch (status) {
                case SUCCEEDED -> {
                    String resultUrl = videoGenClient.resultVideoUrl(clip.getVideoGenJobId());
                    String objectKey = objectStorageService.storeFromUrl(resultUrl, "clips/" + clip.getSocialPostId());
                    clip.setClipObjectKey(objectKey);
                    clip.setStatus(SocialClipStatus.SUCCEEDED);
                }
                case FAILED -> clip.setStatus(SocialClipStatus.FAILED);
                case RUNNING -> {
                    if (clip.getPollAttempts() >= MAX_VIDEO_GEN_POLL_ATTEMPTS) {
                        log.warn(":: SocialPostService - clip {} still RUNNING after {} attempts, giving up ::", clip.getId(), clip.getPollAttempts());
                        clip.setStatus(SocialClipStatus.FAILED);
                    } // else leave RUNNING, retried next poll tick
                }
            }
        } catch (Exception e) {
            failClip(clip, "video-gen poll/fetch", e, MAX_VIDEO_GEN_POLL_ATTEMPTS);
        }
        clipRepository.save(clip);
    }

    /** A clip that keeps throwing (bad URL, provider outage) must not retry forever — bounded attempts, then FAILED, no manual DB fix required. */
    private void failClip(SocialPostClip clip, String stepDescription, Exception e, int maxAttempts) {
        log.warn(":: SocialPostService - {} failed for clip {} (attempt {}/{}): {} ::",
                stepDescription, clip.getId(), clip.getPollAttempts(), maxAttempts, e.getMessage());
        if (clip.getPollAttempts() >= maxAttempts) {
            clip.setStatus(SocialClipStatus.FAILED);
        } // else leave RUNNING/PENDING, retried next poll tick
    }

    /** Stitches every STITCHING post whose clips are all ready. Called by the poller. */
    @Transactional
    public void advanceStitching() {
        for (SocialPost post : postRepository.findByStatusIn(List.of(SocialPostStatus.STITCHING))) {
            try {
                List<String> orderedKeys = clipRepository.findBySocialPostIdOrderBySequenceOrder(post.getId()).stream()
                        .map(SocialPostClip::getClipObjectKey)
                        .toList();
                String combinedKey = videoStitchService.stitch(orderedKeys);
                post.setGeneratedVideoObjectKey(combinedKey);
                post.setStatus(SocialPostStatus.VIDEO_READY_FOR_REVIEW);
                SocialPostRs result = SocialPostRs.from(postRepository.save(post));
                dispatchWebhook(findClient(post), post, enrichWithPreviewUrl(post, result));
            } catch (Exception e) {
                log.warn(":: SocialPostService - stitch failed for post {}: {} ::", post.getId(), e.getMessage());
                failPost(post, SocialFailureStage.STITCHING, "STITCH_FAILED", e.getMessage());
            }
        }
    }

    /** Advances every post waiting on a Meta container to finish processing, then publishes it. Called by the poller. */
    @Transactional
    public void advancePublishing() {
        for (SocialPost post : postRepository.findByStatusIn(List.of(SocialPostStatus.PUBLISHING))) {
            if (post.getMediaContainerId() == null) {
                continue; // VIDEO post approved but container not yet created — shouldn't normally happen, skip defensively
            }
            SocialAccountConfig account = requireActiveAccount(findClient(post), post.getAccountRef(), post.getPlatform());
            SocialPublishClient.ContainerPollResult poll = socialPublishClient.pollContainer(account, post.getMediaContainerId());
            SocialPublishClient.ContainerStatus status = poll.status();
            post.setContainerPollAttempts(post.getContainerPollAttempts() + 1);

            switch (status) {
                case FINISHED -> {
                    String mediaId = socialPublishClient.publish(account, post.getMediaContainerId());
                    post.setInstagramMediaId(mediaId);
                    post.setStatus(SocialPostStatus.PUBLISHED);
                    post.setPublishedAt(Instant.now());
                    SocialPostRs result = SocialPostRs.from(postRepository.save(post));
                    dispatchWebhook(findClient(post), post, result);
                }
                case ERROR, EXPIRED -> {
                    String detail = poll.detail() != null && !poll.detail().isBlank()
                            ? poll.detail()
                            : "Meta container entered " + status;
                    failPost(post, SocialFailureStage.CONTAINER_PROCESSING, "CONTAINER_" + status, detail);
                }
                case IN_PROGRESS -> {
                    if (post.getContainerPollAttempts() >= MAX_CONTAINER_POLL_ATTEMPTS) {
                        failPost(post, SocialFailureStage.CONTAINER_PROCESSING, "CONTAINER_PROCESSING_TIMEOUT", "Container did not finish processing in time");
                    } else {
                        postRepository.save(post);
                    }
                }
            }
        }
    }

    private void failPost(SocialPost post, SocialFailureStage stage, String code, String message) {
        post.setStatus(SocialPostStatus.FAILED);
        post.setFailureStage(stage);
        post.setProviderErrorCode(code);
        post.setProviderErrorMessage(message);
        SocialPostRs result = SocialPostRs.from(postRepository.save(post));
        dispatchWebhook(findClient(post), post, result);
    }

    private void dispatchWebhook(SocialClient client, SocialPost post, SocialPostRs payload) {
        if (post.getCallbackUrl() != null && !post.getCallbackUrl().isBlank()) {
            webhookDispatcher.dispatch(client, post.getCallbackUrl(), payload);
        }
    }

    private SocialPostRs enrichWithPreviewUrl(SocialPost post) {
        return enrichWithPreviewUrl(post, SocialPostRs.from(post));
    }

    private SocialPostRs enrichWithPreviewUrl(SocialPost post, SocialPostRs base) {
        if (post.getStatus() == SocialPostStatus.VIDEO_READY_FOR_REVIEW && post.getGeneratedVideoObjectKey() != null) {
            String previewUrl = objectStorageService.presignedUrl(post.getGeneratedVideoObjectKey(), VIDEO_PRESIGNED_URL_TTL_SECONDS);
            return base.toBuilder().videoPreviewUrl(previewUrl).build();
        }
        return base;
    }

    private SocialAccountConfig loadActiveAccount(SocialClient client, SocialPostSubmitRq request) {
        return requireActiveAccount(client, request.getAccountRef(), request.getPlatform());
    }

    private SocialAccountConfig requireActiveAccount(SocialClient client, String accountRef, SocialPlatform platform) {
        return accountConfigRepository.findBySocialClientIdAndAccountRefAndPlatformAndActiveTrue(client.getId(), accountRef, platform)
                .orElseThrow(() -> ApiException.notFound("No active " + platform + " account configured for accountRef=" + accountRef));
    }

    private SocialPost loadOwnedPost(SocialClient client, Long postId) {
        return postRepository.findByIdAndSocialClientId(postId, client.getId())
                .orElseThrow(() -> ApiException.notFound("Post not found"));
    }

    // advance* methods run off the poller, with no request-scoped "current client" — re-derive it
    // from the post's own socialClientId instead.
    private SocialClient findClient(SocialPost post) {
        return clientRepository.findById(post.getSocialClientId())
                .orElseThrow(() -> new IllegalStateException("SocialPost references unknown SocialClient id=" + post.getSocialClientId()));
    }
}
