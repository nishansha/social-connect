package com.triasoft.socialpublisher.service.impl;

import com.triasoft.socialpublisher.client.VideoGenClient;
import com.triasoft.socialpublisher.exception.ApiException;
import com.triasoft.socialpublisher.service.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Free, local stand-in for {@link VideoGenClient} used when a video post has no prompt text (AI
 * generation disabled — see {@code AppConfigEnum.SOCIAL_AI_VIDEO_GENERATION_ENABLED}): a slow
 * zoom/pan ("Ken Burns" effect) over the still source photo, rendered entirely by ffmpeg, no
 * external API call. Deliberately NOT a {@link VideoGenClient} implementation — that interface is
 * an async remote-job contract (submit now, poll later); this is synchronous and fully local, so
 * forcing it through submit/poll/resultVideoUrl would mean faking a job id and a poll() that lies
 * about being asynchronous.
 *
 * <p>Output params (duration/fps/codec/resolution) are fixed constants, not configurable per call —
 * {@code FfmpegVideoStitchService} concatenates every clip in a post via stream-copy (no re-encode),
 * which requires all clips (AI-generated or Ken Burns) to share resolution/codec/fps/timebase
 * exactly. Canvas is picked from orientation only; duration/fps/codec never vary.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KenBurnsVideoGenService {

    private static final long FFMPEG_TIMEOUT_SECONDS = 60;
    private static final int DURATION_SECONDS = 5;
    private static final int FPS = 30;
    private static final int LANDSCAPE_WIDTH = 1920;
    private static final int LANDSCAPE_HEIGHT = 1080;
    private static final int PORTRAIT_WIDTH = 1080;
    private static final int PORTRAIT_HEIGHT = 1920;
    /** Slight zoom-in over the clip's full frame count — classic Ken Burns pan/zoom, not a jarring snap. */
    private static final double ZOOM_PER_FRAME = 0.0008;

    private final ObjectStorageService objectStorageService;

    public String generate(String photoUrl, VideoGenClient.VideoOrientation orientation) {
        Path workDir;
        try {
            workDir = Files.createTempDirectory("social-kenburns-");
        } catch (IOException e) {
            throw new IllegalStateException("Could not create temp dir for Ken Burns generation", e);
        }
        try {
            byte[] photoBytes = objectStorageService.downloadFromUrl(photoUrl);
            Path input = workDir.resolve("input");
            Files.write(input, photoBytes);
            Path output = workDir.resolve("output.mp4");
            runFfmpeg(input, output, orientation);
            byte[] result = Files.readAllBytes(output);
            return objectStorageService.store(result, "clips", ".mp4", "video/mp4");
        } catch (IOException e) {
            throw new IllegalStateException("Ken Burns generation failed", e);
        } finally {
            deleteQuietly(workDir);
        }
    }

    private void runFfmpeg(Path input, Path output, VideoGenClient.VideoOrientation orientation) {
        boolean portrait = orientation == VideoGenClient.VideoOrientation.PORTRAIT;
        int width = portrait ? PORTRAIT_WIDTH : LANDSCAPE_WIDTH;
        int height = portrait ? PORTRAIT_HEIGHT : LANDSCAPE_HEIGHT;
        int totalFrames = DURATION_SECONDS * FPS;

        String zoompan = "scale=%d:%d:force_original_aspect_ratio=increase,crop=%d:%d,zoompan=z='min(zoom+%s,1.5)':d=%d:s=%dx%d:fps=%d"
                .formatted(width, height, width, height, ZOOM_PER_FRAME, totalFrames, width, height, FPS);

        List<String> command = List.of(
                "ffmpeg", "-y",
                "-loop", "1",
                "-i", input.toAbsolutePath().toString(),
                "-vf", zoompan,
                "-t", String.valueOf(DURATION_SECONDS),
                "-c:v", "libx264",
                "-pix_fmt", "yuv420p",
                "-an",
                output.toAbsolutePath().toString()
        );
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(FFMPEG_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            String ffmpegOutput = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            if (!finished) {
                process.destroyForcibly();
                throw ApiException.badRequest("ffmpeg timed out after " + FFMPEG_TIMEOUT_SECONDS + "s");
            }
            if (process.exitValue() != 0) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ffmpeg exited " + process.exitValue() + ": " + ffmpegOutput.strip());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Failed to run ffmpeg", e);
        }
    }

    private void deleteQuietly(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // best-effort cleanup only
                        }
                    });
        } catch (IOException e) {
            log.warn(":: KenBurnsVideoGenService - failed to clean up {}: {} ::", dir, e.getMessage());
        }
    }
}
