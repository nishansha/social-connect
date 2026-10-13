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
import com.triasoft.socialpublisher.enums.SocialPlatform;
import com.triasoft.socialpublisher.enums.SocialPostStatus;
import com.triasoft.socialpublisher.enums.SocialPostType;
import com.triasoft.socialpublisher.exception.ApiException;
import com.triasoft.socialpublisher.repository.SocialAccountConfigRepository;
import com.triasoft.socialpublisher.repository.SocialClientRepository;
import com.triasoft.socialpublisher.repository.SocialPostClipRepository;
import com.triasoft.socialpublisher.repository.SocialPostRepository;
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

    private SocialPostService service;
    private SocialClient client;
    private SocialAccountConfig account;

    @BeforeEach
    void setUp() {
        service = new SocialPostService(postRepository, clipRepository, accountConfigRepository, clientRepository,
                socialPublishClient, videoGenClient, videoStitchService, objectStorageService, webhookDispatcher);

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
        when(socialPublishClient.createPhotoContainer(eq(account), anyString(), anyString())).thenReturn("container-1");

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
        verify(socialPublishClient, never()).createPhotoContainer(any(), anyString(), anyString());
    }

    @Test
    void submitVideo_submitsOneVideoGenJobPerPhoto() {
        when(accountConfigRepository.findBySocialClientIdAndAccountRefAndPlatformAndActiveTrue(1L, "dealer-1", SocialPlatform.INSTAGRAM))
                .thenReturn(Optional.of(account));
        when(videoGenClient.submit(anyString(), anyString())).thenReturn("job-a", "job-b");

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
        when(socialPublishClient.pollContainer(account, "container-1")).thenReturn(SocialPublishClient.ContainerStatus.FINISHED);
        when(socialPublishClient.publish(account, "container-1")).thenReturn("ig-media-1");

        service.advancePublishing();

        assertThat(post.getStatus()).isEqualTo(SocialPostStatus.PUBLISHED);
        assertThat(post.getInstagramMediaId()).isEqualTo("ig-media-1");
        assertThat(post.getPublishedAt()).isNotNull();
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
        when(socialPublishClient.pollContainer(account, "container-1")).thenReturn(SocialPublishClient.ContainerStatus.IN_PROGRESS);

        service.advancePublishing();

        assertThat(post.getStatus()).isEqualTo(SocialPostStatus.FAILED);
        assertThat(post.getProviderErrorCode()).isEqualTo("CONTAINER_PROCESSING_TIMEOUT");
        verify(socialPublishClient, never()).publish(any(), anyString());
    }
}
