package com.triasoft.socialpublisher.service.impl;

import com.triasoft.socialpublisher.exception.ApiException;
import com.triasoft.socialpublisher.service.ObjectStorageService;
import com.triasoft.socialpublisher.service.VideoStitchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Concatenates N per-photo clips into one video via ffmpeg's concat demuxer (stream copy, no
 * re-encode — all clips come from the same provider/model, so codecs should already match).
 * Requires the {@code ffmpeg} binary on PATH — see this service's Dockerfile.
 *
 * <p>Known open item from the plan, not yet resolved here: whether the chosen video-gen model's
 * raw output already matches Instagram Reels' format constraints (aspect/duration/codec). If not,
 * this is where a transcode pass (re-encode instead of stream copy) would need to be added — spike
 * that against the real model's output before relying on raw concat in production.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FfmpegVideoStitchService implements VideoStitchService {

    private static final long FFMPEG_TIMEOUT_SECONDS = 120;

    private final ObjectStorageService objectStorageService;

    @Override
    public String stitch(List<String> clipObjectKeysInOrder) {
        Path workDir;
        try {
            workDir = Files.createTempDirectory("social-stitch-");
        } catch (IOException e) {
            throw new IllegalStateException("Could not create temp dir for stitching", e);
        }
        try {
            List<Path> localClips = downloadClips(clipObjectKeysInOrder, workDir);
            Path concatList = writeConcatList(localClips, workDir);
            Path output = workDir.resolve("output.mp4");
            runFfmpeg(concatList, output);
            byte[] result = Files.readAllBytes(output);
            return objectStorageService.store(result, "videos", ".mp4", "video/mp4");
        } catch (IOException e) {
            throw new IllegalStateException("Stitching failed", e);
        } finally {
            deleteQuietly(workDir);
        }
    }

    private List<Path> downloadClips(List<String> objectKeys, Path workDir) throws IOException {
        List<Path> localPaths = new ArrayList<>();
        for (int i = 0; i < objectKeys.size(); i++) {
            byte[] content = objectStorageService.download(objectKeys.get(i));
            Path path = workDir.resolve("clip-" + i + ".mp4");
            Files.write(path, content);
            localPaths.add(path);
        }
        return localPaths;
    }

    private Path writeConcatList(List<Path> localClips, Path workDir) throws IOException {
        Path listFile = workDir.resolve("concat.txt");
        StringBuilder sb = new StringBuilder();
        for (Path clip : localClips) {
            sb.append("file '").append(clip.toAbsolutePath()).append("'\n");
        }
        Files.writeString(listFile, sb.toString(), StandardCharsets.UTF_8);
        return listFile;
    }

    private void runFfmpeg(Path concatList, Path output) {
        List<String> command = List.of(
                "ffmpeg", "-y",
                "-f", "concat", "-safe", "0",
                "-i", concatList.toAbsolutePath().toString(),
                "-c", "copy",
                output.toAbsolutePath().toString()
        );
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(FFMPEG_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            String ffmpegOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
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
            log.warn(":: FfmpegVideoStitchService - failed to clean up {}: {} ::", dir, e.getMessage());
        }
    }
}
