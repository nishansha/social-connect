package com.triasoft.socialconnect.service;

import com.triasoft.socialconnect.client.SocialPublishClient;
import com.triasoft.socialconnect.client.VideoGenClient;
import com.triasoft.socialconnect.dto.SocialPostRs;
import com.triasoft.socialconnect.dto.SocialPostSubmitRq;
import com.triasoft.socialconnect.entity.SocialAccountConfig;
import com.triasoft.socialconnect.entity.SocialClient;
import com.triasoft.socialconnect.entity.SocialPost;
import com.triasoft.socialconnect.entity.SocialPostClip;
import com.triasoft.socialconnect.enums.SocialClipStatus;
import com.triasoft.socialconnect.enums.SocialPlatform;
import com.triasoft.socialconnect.enums.SocialPostStatus;
import com.triasoft.socialconnect.enums.SocialPostType;
import com.triasoft.socialconnect.exception.ApiException;
import com.triasoft.socialconnect.repository.SocialAccountConfigRepository;
import com.triasoft.socialconnect.repository.SocialClientRepository;
import com.triasoft.socialconnect.repository.SocialPostClipRepository;
import com.triasoft.socialconnect.repository.SocialPostRepository;
import com.triasoft.socialconnect.service.impl.KenBurnsVideoGenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialPostServiceTest {

    @Mock
    private SocialPostRepository postRepository;
    @Mock
    private SocialPostClipRepository clipRepository;
    @Mock
    private SocialAccountConfigRepository accountConfigRepository;
    @Mock
    private SocialClientRepository clientRepository;
    @Mock
    private SocialPublishClient socialPublishClient;
    @Mock
    private VideoGenClient videoGenClient;
    @Mock
    private VideoStitchService videoStitchService;
    @Mock
    private ObjectStorageService objectStorageService;
    @Mock
    private WebhookDispatcher webhookDispatcher;
    @Mock
    private KenBurnsVideoGenService kenBurnsVideoGenService;

    private SocialPostService service;
    private SocialClient client;
    private SocialAccountConfig account;

    @BeforeEach
    void setUp() {
        service = new SocialPostService(postRepository, clipRepository, accountConfigRepository, clientRepository,
                socialPublishClient, videoGenClient, videoStitchService, objectStorageService, webhookDispatcher,
                kenBurnsVideoGenService);

        client = new SocialClient();
        client.setId(1L);
        client.setClientId("garage-api");

        account = new SocialAccountConfig();
        account.setId(10L);
        account.setSocialClientId(1L);
        account.setPlatform(SocialPlatform.INSTAGRAM);
        account.setIgBusinessAccountId("ig-biz-1");

        org.mockito.Mockito.lenient().when(postRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private SocialPostSubmitRq photoRequest() {
        SocialPostSubmitRq rq = new SocialPostSubmitRq();
        rq.setAccountRef("dealer-1");
        rq.setPlatform(SocialPlatform.INSTAGRAM);
        rq.setPostType(SocialPostType.PHOTO);
        rq.setInventoryRef("42");
        rq.setPhotoUrls(List.of("https://minio.example.com/photo.jpg"));
        rq.setCaption("For sale");
        rq.setCallbackUrl("https://garage.example.com/api/v1/webhooks/social-publisher");
        return rq;
    }

    @Test
    void submitPhoto_createsContainerAndPersistsPublishingPost() {
        when(accountConfigRepository.findBySocialClientIdAndAccountRefAndPlatformAndActiveTrue(1L, "dealer-1", SocialPlatform.INSTAGRAM))
                .thenReturn(Optional.of(account));
        when(socialPublishClient.createPhotoContainer(eq(account), anyList(), anyString())).thenReturn("container-1");

        SocialPostRs result = service.submitPhoto(client, photoRequest());

        assertThat(result.getStatus()).isEqualTo("PUBLISHING");
        ArgumentCaptor<SocialPost> captor = ArgumentCaptor.forClass(SocialPost.class);
        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().getMediaContainerId()).isEqualTo("container-1");
        assertThat(captor.getValue().getInventoryRef()).isEqualTo("42");
        assertThat(captor.getValue().getSourcePhotoUrls()).containsExactly("https://minio.example.com/photo.jpg");
    }

    @Test
    void submitPhoto_rejectsWhenNoAccountConfigured() {
        when(accountConfigRepository.findBySocialClientIdAndAccountRefAndPlatformAndActiveTrue(1L, "dealer-1", SocialPlatform.INSTAGRAM))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitPhoto(client, photoRequest()))
                .isInstanceOf(ApiException.class);
        verify(socialPublishClient, never()).createPhotoContainer(any(), anyList(), anyString());
    }

    @Test
    void submitPhoto_withMultiplePhotos_passesTheFullListForACarousel() {
        when(accountConfigRepository.findBySocialClientIdAndAccountRefAndPlatformAndActiveTrue(1L, "dealer-1", SocialPlatform.INSTAGRAM))
                .thenReturn(Optional.of(account));
        when(socialPublishClient.createPhotoContainer(eq(account), anyList(), anyString())).thenReturn("carousel-container-1");

        SocialPostSubmitRq rq = photoRequest();
        rq.setPhotoUrls(List.of(
                "https://minio.example.com/photo-1.jpg",
                "https://minio.example.com/photo-2.jpg",
                "https://minio.example.com/photo-3.jpg"));

        SocialPostRs result = service.submitPhoto(client, rq);

        assertThat(result.getStatus()).isEqualTo("PUBLISHING");
        ArgumentCaptor<List<String>> urlsCaptor = ArgumentCaptor.forClass(List.class);
        verify(socialPublishClient).createPhotoContainer(eq(account), urlsCaptor.capture(), eq("For sale"));
        assertThat(urlsCaptor.getValue()).containsExactly(
                "https://minio.example.com/photo-1.jpg",
                "https://minio.example.com/photo-2.jpg",
                "https://minio.example.com/photo-3.jpg");

        ArgumentCaptor<SocialPost> postCaptor = ArgumentCaptor.forClass(SocialPost.class);
        verify(postRepository).save(postCaptor.capture());
        assertThat(postCaptor.getValue().getSourcePhotoUrls()).hasSize(3);
        assertThat(postCaptor.getValue().getMediaContainerId()).isEqualTo("carousel-container-1");
    }

    @Test
    void submitPhoto_rejectsMoreThanTenPhotos() {
        SocialPostSubmitRq rq = photoRequest();
        rq.setPhotoUrls(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11").stream()
                .map(n -> "https://minio.example.com/photo-" + n + ".jpg")
                .toList());

        assertThatThrownBy(() -> service.submitPhoto(client, rq)).isInstanceOf(ApiException.class);
        verify(socialPublishClient, never()).createPhotoContainer(any(), anyList(), anyString());
    }

    @Test
    void submitVideo_submitsOneVideoGenJobPerPhoto() {
        when(accountConfigRepository.findBySocialClientIdAndAccountRefAndPlatformAndActiveTrue(1L, "dealer-1", SocialPlatform.INSTAGRAM))
                .thenReturn(Optional.of(account));
        // These URLs aren't reachable, so orientation detection falls back to LANDSCAPE — this
        // test doubles as the fallback-behavior guard (a submission must never be blocked by a
        // failed dimension probe) and confirms the SAME orientation reaches every clip.
        when(videoGenClient.submit(anyString(), anyString(), eq(VideoGenClient.VideoOrientation.LANDSCAPE)))
                .thenReturn("job-a", "job-b");

        SocialPostSubmitRq rq = new SocialPostSubmitRq();
        rq.setAccountRef("dealer-1");
        rq.setPlatform(SocialPlatform.INSTAGRAM);
        rq.setPostType(SocialPostType.VIDEO);
        rq.setInventoryRef("42");
        rq.setPhotoUrls(List.of("https://minio.example.com/a.jpg", "https://minio.example.com/b.jpg"));
        rq.setPrompt("slow pan on the exterior");
        rq.setCallbackUrl("https://garage.example.com/webhook");

        when(postRepository.save(any())).thenAnswer(inv -> {
            SocialPost p = inv.getArgument(0);
            p.setId(100L);
            return p;
        });

        SocialPostRs result = service.submitVideo(client, rq);

        assertThat(result.getStatus()).isEqualTo("PENDING_VIDEO_GEN");
        ArgumentCaptor<SocialPostClip> clipCaptor = ArgumentCaptor.forClass(SocialPostClip.class);
        verify(clipRepository, org.mockito.Mockito.times(2)).save(clipCaptor.capture());
        List<SocialPostClip> clips = clipCaptor.getAllValues();
        assertThat(clips).extracting(SocialPostClip::getSequenceOrder).containsExactly(0, 1);
        assertThat(clips).extracting(SocialPostClip::getVideoGenJobId).containsExactly("job-a", "job-b");
        assertThat(clips).extracting(SocialPostClip::getStatus).containsOnly(SocialClipStatus.RUNNING);
        verify(videoGenClient, org.mockito.Mockito.times(2))
                .submit(anyString(), eq("slow pan on the exterior"), eq(VideoGenClient.VideoOrientation.LANDSCAPE));
    }

    @Test
    void submitVideo_detectsPortraitFromTheFirstPhotosRealDimensions() throws Exception {
        when(accountConfigRepository.findBySocialClientIdAndAccountRefAndPlatformAndActiveTrue(1L, "dealer-1", SocialPlatform.INSTAGRAM))
                .thenReturn(Optional.of(account));
        when(videoGenClient.submit(anyString(), anyString(), eq(VideoGenClient.VideoOrientation.PORTRAIT)))
                .thenReturn("job-a");
        when(postRepository.save(any())).thenAnswer(inv -> {
            SocialPost p = inv.getArgument(0);
            p.setId(200L);
            return p;
        });

        String portraitImageUrl = writeTempTestImage(480, 960); // taller than wide

        SocialPostSubmitRq rq = new SocialPostSubmitRq();
        rq.setAccountRef("dealer-1");
        rq.setPlatform(SocialPlatform.INSTAGRAM);
        rq.setPostType(SocialPostType.VIDEO);
        rq.setInventoryRef("42");
        rq.setPhotoUrls(List.of(portraitImageUrl));
        rq.setPrompt("slow pan");
        rq.setCallbackUrl("https://garage.example.com/webhook");

        SocialPostRs result = service.submitVideo(client, rq);

        verify(videoGenClient).submit(eq(portraitImageUrl), anyString(), eq(VideoGenClient.VideoOrientation.PORTRAIT));
        // orientation is persisted on the post (not just a local variable) — advanceClip needs it
        // again later, off the request thread, for the Ken Burns branch.
        ArgumentCaptor<SocialPost> postCaptor = ArgumentCaptor.forClass(SocialPost.class);
        verify(postRepository).save(postCaptor.capture());
        assertThat(postCaptor.getValue().getOrientation()).isEqualTo(VideoGenClient.VideoOrientation.PORTRAIT);
        assertThat(result.getStatus()).isEqualTo("PENDING_VIDEO_GEN");
    }

    @Test
    void submitVideo_withNoPrompt_fallsBackToKenBurnsAndNeverCallsVideoGenClient() {
        when(accountConfigRepository.findBySocialClientIdAndAccountRefAndPlatformAndActiveTrue(1L, "dealer-1", SocialPlatform.INSTAGRAM))
                .thenReturn(Optional.of(account));
        when(postRepository.save(any())).thenAnswer(inv -> {
            SocialPost p = inv.getArgument(0);
            p.setId(300L);
            return p;
        });

        SocialPostSubmitRq rq = new SocialPostSubmitRq();
        rq.setAccountRef("dealer-1");
        rq.setPlatform(SocialPlatform.INSTAGRAM);
        rq.setPostType(SocialPostType.VIDEO);
        rq.setInventoryRef("42");
        rq.setPhotoUrls(List.of("https://minio.example.com/a.jpg", "https://minio.example.com/b.jpg"));
        rq.setPrompt(null); // AI generation disabled client-side
        rq.setCallbackUrl("https://garage.example.com/webhook");

        SocialPostRs result = service.submitVideo(client, rq);

        assertThat(result.getStatus()).isEqualTo("PENDING_VIDEO_GEN");
        org.mockito.Mockito.verifyNoInteractions(videoGenClient);
        ArgumentCaptor<SocialPostClip> clipCaptor = ArgumentCaptor.forClass(SocialPostClip.class);
        verify(clipRepository, org.mockito.Mockito.times(2)).save(clipCaptor.capture());
        assertThat(clipCaptor.getAllValues())
                .extracting(SocialPostClip::getVideoGenProvider, SocialPostClip::getStatus, SocialPostClip::getVideoGenJobId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("ffmpeg-kenburns", SocialClipStatus.PENDING, null),
                        org.assertj.core.groups.Tuple.tuple("ffmpeg-kenburns", SocialClipStatus.PENDING, null));
    }

    @Test
    void submitVideo_withBlankPrompt_alsoFallsBackToKenBurns() {
        when(accountConfigRepository.findBySocialClientIdAndAccountRefAndPlatformAndActiveTrue(1L, "dealer-1", SocialPlatform.INSTAGRAM))
                .thenReturn(Optional.of(account));
        when(postRepository.save(any())).thenAnswer(inv -> {
            SocialPost p = inv.getArgument(0);
            p.setId(301L);
            return p;
        });

        SocialPostSubmitRq rq = new SocialPostSubmitRq();
        rq.setAccountRef("dealer-1");
        rq.setPlatform(SocialPlatform.INSTAGRAM);
        rq.setPostType(SocialPostType.VIDEO);
        rq.setInventoryRef("42");
        rq.setPhotoUrls(List.of("https://minio.example.com/a.jpg"));
        rq.setPrompt("   "); // blank, not just null
        rq.setCallbackUrl("https://garage.example.com/webhook");

        service.submitVideo(client, rq);

        org.mockito.Mockito.verifyNoInteractions(videoGenClient);
        ArgumentCaptor<SocialPostClip> clipCaptor = ArgumentCaptor.forClass(SocialPostClip.class);
        verify(clipRepository).save(clipCaptor.capture());
        assertThat(clipCaptor.getValue().getVideoGenProvider()).isEqualTo("ffmpeg-kenburns");
    }

    @Test
    void advanceVideoGenJobs_withKenBurnsClip_generatesSynchronouslyAndNeverPollsVideoGenClient() {
        SocialPost post = new SocialPost();
        post.setId(100L);
        post.setStatus(SocialPostStatus.PENDING_VIDEO_GEN);
        post.setOrientation(VideoGenClient.VideoOrientation.PORTRAIT);
        when(postRepository.findByStatusIn(List.of(SocialPostStatus.PENDING_VIDEO_GEN))).thenReturn(List.of(post));

        SocialPostClip clip = new SocialPostClip();
        clip.setSocialPostId(100L);
        clip.setSourcePhotoUrl("https://minio.example.com/a.jpg");
        clip.setVideoGenProvider("ffmpeg-kenburns");
        clip.setStatus(SocialClipStatus.PENDING);
        when(clipRepository.findBySocialPostIdOrderBySequenceOrder(100L)).thenReturn(List.of(clip));
        when(kenBurnsVideoGenService.generate("https://minio.example.com/a.jpg", VideoGenClient.VideoOrientation.PORTRAIT))
                .thenReturn("clips/100/clip-0.mp4");

        service.advanceVideoGenJobs();

        assertThat(clip.getStatus()).isEqualTo(SocialClipStatus.SUCCEEDED);
        assertThat(clip.getClipObjectKey()).isEqualTo("clips/100/clip-0.mp4");
        assertThat(post.getStatus()).isEqualTo(SocialPostStatus.STITCHING);
        org.mockito.Mockito.verifyNoInteractions(videoGenClient);
    }

    @Test
    void advanceVideoGenJobs_retriesAKenBurnsFailureBeforeGivingUp() {
        SocialPost post = new SocialPost();
        post.setId(100L);
        post.setStatus(SocialPostStatus.PENDING_VIDEO_GEN);
        post.setOrientation(VideoGenClient.VideoOrientation.LANDSCAPE);
        when(postRepository.findByStatusIn(List.of(SocialPostStatus.PENDING_VIDEO_GEN))).thenReturn(List.of(post));

        SocialPostClip clip = new SocialPostClip();
        clip.setSocialPostId(100L);
        clip.setSourcePhotoUrl("https://minio.example.com/expired.jpg");
        clip.setVideoGenProvider("ffmpeg-kenburns");
        clip.setStatus(SocialClipStatus.PENDING);
        clip.setPollAttempts(0);
        when(clipRepository.findBySocialPostIdOrderBySequenceOrder(100L)).thenReturn(List.of(clip));
        when(kenBurnsVideoGenService.generate(anyString(), any()))
                .thenThrow(new IllegalStateException("403 AccessDenied"));

        // First failure: pollAttempts becomes 1, still under MAX_KEN_BURNS_ATTEMPTS (3) — must
        // not be marked FAILED yet, and the post must stay PENDING_VIDEO_GEN for a retry.
        service.advanceVideoGenJobs();
        assertThat(clip.getStatus()).isEqualTo(SocialClipStatus.PENDING);
        assertThat(clip.getPollAttempts()).isEqualTo(1);
        assertThat(post.getStatus()).isEqualTo(SocialPostStatus.PENDING_VIDEO_GEN);
    }

    @Test
    void advanceVideoGenJobs_failsAKenBurnsClipAfterMaxAttempts_noManualDbFixNeeded() {
        SocialPost post = new SocialPost();
        post.setId(100L);
        post.setStatus(SocialPostStatus.PENDING_VIDEO_GEN);
        post.setOrientation(VideoGenClient.VideoOrientation.LANDSCAPE);
        when(postRepository.findByStatusIn(List.of(SocialPostStatus.PENDING_VIDEO_GEN))).thenReturn(List.of(post));
        when(clientRepository.findById(any())).thenReturn(Optional.of(client));

        SocialPostClip clip = new SocialPostClip();
        clip.setSocialPostId(100L);
        clip.setSourcePhotoUrl("https://minio.example.com/expired.jpg");
        clip.setVideoGenProvider("ffmpeg-kenburns");
        clip.setStatus(SocialClipStatus.PENDING);
        clip.setPollAttempts(2); // one more failure hits MAX_KEN_BURNS_ATTEMPTS (3)
        when(clipRepository.findBySocialPostIdOrderBySequenceOrder(100L)).thenReturn(List.of(clip));
        when(kenBurnsVideoGenService.generate(anyString(), any()))
                .thenThrow(new IllegalStateException("403 AccessDenied"));

        service.advanceVideoGenJobs();

        assertThat(clip.getStatus()).isEqualTo(SocialClipStatus.FAILED);
        assertThat(post.getStatus()).isEqualTo(SocialPostStatus.FAILED);
    }

    @Test
    void advanceVideoGenJobs_failsAStuckRunningReplicateClipAfterMaxPollAttempts() {
        SocialPost post = new SocialPost();
        post.setId(100L);
        post.setStatus(SocialPostStatus.PENDING_VIDEO_GEN);
        when(postRepository.findByStatusIn(List.of(SocialPostStatus.PENDING_VIDEO_GEN))).thenReturn(List.of(post));
        when(clientRepository.findById(any())).thenReturn(Optional.of(client));

        SocialPostClip clip = new SocialPostClip();
        clip.setSocialPostId(100L);
        clip.setVideoGenJobId("job-stuck");
        clip.setStatus(SocialClipStatus.RUNNING);
        clip.setPollAttempts(19); // one more RUNNING poll hits MAX_VIDEO_GEN_POLL_ATTEMPTS (20)
        when(clipRepository.findBySocialPostIdOrderBySequenceOrder(100L)).thenReturn(List.of(clip));
        when(videoGenClient.poll("job-stuck")).thenReturn(VideoGenClient.VideoGenStatus.RUNNING);

        service.advanceVideoGenJobs();

        assertThat(clip.getStatus()).isEqualTo(SocialClipStatus.FAILED);
        assertThat(post.getStatus()).isEqualTo(SocialPostStatus.FAILED);
    }

    /** Writes a tiny real PNG to a temp file and returns its file:// URL — ImageIO can read dimensions from this with no network involved. */
    private String writeTempTestImage(int width, int height) throws Exception {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.File file = java.io.File.createTempFile("social-post-service-test-", ".png");
        file.deleteOnExit();
        javax.imageio.ImageIO.write(image, "png", file);
        return file.toURI().toURL().toString();
    }

    @Test
    void advanceVideoGenJobs_movesToStitchingOnceAllClipsSucceed() {
        SocialPost post = new SocialPost();
        post.setId(100L);
        post.setStatus(SocialPostStatus.PENDING_VIDEO_GEN);
        when(postRepository.findByStatusIn(List.of(SocialPostStatus.PENDING_VIDEO_GEN))).thenReturn(List.of(post));

        SocialPostClip clip = new SocialPostClip();
        clip.setSocialPostId(100L);
        clip.setVideoGenJobId("job-a");
        clip.setStatus(SocialClipStatus.RUNNING);
        when(clipRepository.findBySocialPostIdOrderBySequenceOrder(100L)).thenReturn(List.of(clip));

        when(videoGenClient.poll("job-a")).thenReturn(VideoGenClient.VideoGenStatus.SUCCEEDED);
        when(videoGenClient.resultVideoUrl("job-a")).thenReturn("https://replicate.delivery/result.mp4");
        when(objectStorageService.storeFromUrl(eq("https://replicate.delivery/result.mp4"), anyString())).thenReturn("clips/100/clip-0.mp4");

        service.advanceVideoGenJobs();

        assertThat(clip.getStatus()).isEqualTo(SocialClipStatus.SUCCEEDED);
        assertThat(clip.getClipObjectKey()).isEqualTo("clips/100/clip-0.mp4");
        assertThat(post.getStatus()).isEqualTo(SocialPostStatus.STITCHING);
    }

    @Test
    void advanceVideoGenJobs_failsWholePostWhenAnyClipFails() {
        SocialPost post = new SocialPost();
        post.setId(100L);
        post.setStatus(SocialPostStatus.PENDING_VIDEO_GEN);
        when(postRepository.findByStatusIn(List.of(SocialPostStatus.PENDING_VIDEO_GEN))).thenReturn(List.of(post));

        SocialPostClip okClip = new SocialPostClip();
        okClip.setStatus(SocialClipStatus.SUCCEEDED);
        okClip.setVideoGenJobId("job-a");
        SocialPostClip failedClip = new SocialPostClip();
        failedClip.setStatus(SocialClipStatus.RUNNING);
        failedClip.setVideoGenJobId("job-b");
        when(clipRepository.findBySocialPostIdOrderBySequenceOrder(100L)).thenReturn(List.of(okClip, failedClip));
        when(videoGenClient.poll("job-b")).thenReturn(VideoGenClient.VideoGenStatus.FAILED);

        when(clientRepository.findById(any())).thenReturn(Optional.of(client));

        service.advanceVideoGenJobs();

        assertThat(post.getStatus()).isEqualTo(SocialPostStatus.FAILED);
        assertThat(post.getFailureStage().name()).isEqualTo("VIDEO_GEN");
    }

    @Test
    void advanceStitching_movesToReadyForReviewAndDispatchesWebhook() {
        SocialPost post = new SocialPost();
        post.setId(100L);
        post.setSocialClientId(1L);
        post.setCallbackUrl("https://garage.example.com/webhook");
        post.setStatus(SocialPostStatus.STITCHING);
        when(postRepository.findByStatusIn(List.of(SocialPostStatus.STITCHING))).thenReturn(List.of(post));

        SocialPostClip clip = new SocialPostClip();
        clip.setClipObjectKey("clips/100/clip-0.mp4");
        when(clipRepository.findBySocialPostIdOrderBySequenceOrder(100L)).thenReturn(List.of(clip));
        when(videoStitchService.stitch(List.of("clips/100/clip-0.mp4"))).thenReturn("videos/100/final.mp4");
        when(objectStorageService.presignedUrl(eq("videos/100/final.mp4"), eq(15 * 60))).thenReturn("https://minio.example.com/final.mp4?sig=x");
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));

        service.advanceStitching();

        assertThat(post.getStatus()).isEqualTo(SocialPostStatus.VIDEO_READY_FOR_REVIEW);
        assertThat(post.getGeneratedVideoObjectKey()).isEqualTo("videos/100/final.mp4");
        verify(webhookDispatcher).dispatch(eq(client), eq("https://garage.example.com/webhook"), any());
    }

    @Test
    void approve_rejectsWhenNotAwaitingReview() {
        SocialPost post = new SocialPost();
        post.setId(100L);
        post.setStatus(SocialPostStatus.PUBLISHING);
        when(postRepository.findByIdAndSocialClientId(100L, 1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> service.approve(client, 100L)).isInstanceOf(ApiException.class);
        verify(socialPublishClient, never()).createVideoContainer(any(), anyString(), any());
    }

    @Test
    void approve_createsVideoContainerAndMovesToPublishing() {
        SocialPost post = new SocialPost();
        post.setId(100L);
        post.setAccountRef("dealer-1");
        post.setStatus(SocialPostStatus.VIDEO_READY_FOR_REVIEW);
        post.setPlatform(SocialPlatform.INSTAGRAM);
        post.setGeneratedVideoObjectKey("videos/100/final.mp4");
        post.setCallbackUrl("https://garage.example.com/webhook");
        when(postRepository.findByIdAndSocialClientId(100L, 1L)).thenReturn(Optional.of(post));
        when(accountConfigRepository.findBySocialClientIdAndAccountRefAndPlatformAndActiveTrue(1L, "dealer-1", SocialPlatform.INSTAGRAM)).thenReturn(Optional.of(account));
        when(objectStorageService.presignedUrl("videos/100/final.mp4", 15 * 60)).thenReturn("https://minio.example.com/final.mp4?sig=x");
        when(socialPublishClient.createVideoContainer(account, "https://minio.example.com/final.mp4?sig=x", null)).thenReturn("container-2");

        SocialPostRs result = service.approve(client, 100L);

        assertThat(result.getStatus()).isEqualTo("PUBLISHING");
        assertThat(post.getMediaContainerId()).isEqualTo("container-2");
        assertThat(post.getApprovedAt()).isNotNull();
    }

    @Test
    void advancePublishing_publishesWhenContainerFinished() {
        SocialPost post = new SocialPost();
        post.setId(100L);
        post.setSocialClientId(1L);
        post.setAccountRef("dealer-1");
        post.setPlatform(SocialPlatform.INSTAGRAM);
        post.setMediaContainerId("container-1");
        post.setStatus(SocialPostStatus.PUBLISHING);
        when(postRepository.findByStatusIn(List.of(SocialPostStatus.PUBLISHING))).thenReturn(List.of(post));
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(accountConfigRepository.findBySocialClientIdAndAccountRefAndPlatformAndActiveTrue(1L, "dealer-1", SocialPlatform.INSTAGRAM)).thenReturn(Optional.of(account));
        when(socialPublishClient.pollContainer(account, "container-1"))
                .thenReturn(new SocialPublishClient.ContainerPollResult(SocialPublishClient.ContainerStatus.FINISHED, null));
        when(socialPublishClient.publish(account, "container-1")).thenReturn("ig-media-1");

        service.advancePublishing();

        assertThat(post.getStatus()).isEqualTo(SocialPostStatus.PUBLISHED);
        assertThat(post.getInstagramMediaId()).isEqualTo("ig-media-1");
        assertThat(post.getPublishedAt()).isNotNull();
    }

    @Test
    void advancePublishing_surfacesMetasOwnErrorDetailInsteadOfAGenericMessage() {
        SocialPost post = new SocialPost();
        post.setId(100L);
        post.setSocialClientId(1L);
        post.setAccountRef("dealer-1");
        post.setPlatform(SocialPlatform.INSTAGRAM);
        post.setMediaContainerId("container-1");
        post.setStatus(SocialPostStatus.PUBLISHING);
        when(postRepository.findByStatusIn(List.of(SocialPostStatus.PUBLISHING))).thenReturn(List.of(post));
        when(clientRepository.findById(any())).thenReturn(Optional.of(client));
        when(accountConfigRepository.findBySocialClientIdAndAccountRefAndPlatformAndActiveTrue(1L, "dealer-1", SocialPlatform.INSTAGRAM)).thenReturn(Optional.of(account));
        when(socialPublishClient.pollContainer(account, "container-1")).thenReturn(
                new SocialPublishClient.ContainerPollResult(
                        SocialPublishClient.ContainerStatus.ERROR,
                        "Video could not be processed because the video asset does not meet Reels requirements."));

        service.advancePublishing();

        assertThat(post.getStatus()).isEqualTo(SocialPostStatus.FAILED);
        assertThat(post.getProviderErrorMessage())
                .isEqualTo("Video could not be processed because the video asset does not meet Reels requirements.");
    }

    @Test
    void advancePublishing_fallsBackToAGenericMessageWhenMetaGivesNoDetail() {
        SocialPost post = new SocialPost();
        post.setId(100L);
        post.setSocialClientId(1L);
        post.setAccountRef("dealer-1");
        post.setPlatform(SocialPlatform.INSTAGRAM);
        post.setMediaContainerId("container-1");
        post.setStatus(SocialPostStatus.PUBLISHING);
        when(postRepository.findByStatusIn(List.of(SocialPostStatus.PUBLISHING))).thenReturn(List.of(post));
        when(clientRepository.findById(any())).thenReturn(Optional.of(client));
        when(accountConfigRepository.findBySocialClientIdAndAccountRefAndPlatformAndActiveTrue(1L, "dealer-1", SocialPlatform.INSTAGRAM)).thenReturn(Optional.of(account));
        when(socialPublishClient.pollContainer(account, "container-1"))
                .thenReturn(new SocialPublishClient.ContainerPollResult(SocialPublishClient.ContainerStatus.ERROR, null));

        service.advancePublishing();

        assertThat(post.getProviderErrorMessage()).isEqualTo("Meta container entered ERROR");
    }

    @Test
    void advancePublishing_timesOutAfterMaxPollAttempts() {
        SocialPost post = new SocialPost();
        post.setId(100L);
        post.setSocialClientId(1L);
        post.setAccountRef("dealer-1");
        post.setPlatform(SocialPlatform.INSTAGRAM);
        post.setMediaContainerId("container-1");
        post.setStatus(SocialPostStatus.PUBLISHING);
        post.setContainerPollAttempts(9); // one short of the 10-attempt cap
        when(postRepository.findByStatusIn(List.of(SocialPostStatus.PUBLISHING))).thenReturn(List.of(post));
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(accountConfigRepository.findBySocialClientIdAndAccountRefAndPlatformAndActiveTrue(1L, "dealer-1", SocialPlatform.INSTAGRAM)).thenReturn(Optional.of(account));
        when(socialPublishClient.pollContainer(account, "container-1"))
                .thenReturn(new SocialPublishClient.ContainerPollResult(SocialPublishClient.ContainerStatus.IN_PROGRESS, null));

        service.advancePublishing();

        assertThat(post.getStatus()).isEqualTo(SocialPostStatus.FAILED);
        assertThat(post.getProviderErrorCode()).isEqualTo("CONTAINER_PROCESSING_TIMEOUT");
        verify(socialPublishClient, never()).publish(any(), anyString());
    }
}
