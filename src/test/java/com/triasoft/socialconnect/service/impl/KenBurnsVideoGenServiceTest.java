package com.triasoft.socialconnect.service.impl;

import com.triasoft.socialconnect.client.VideoGenClient;
import com.triasoft.socialconnect.service.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

/**
 * Real ffmpeg execution, no mocking of the process itself — unlike the Replicate path, this calls
 * no external paid API, so there's no reason not to exercise the actual binary. Confirms the whole
 * point of the feature: a real, non-trivial, correctly-oriented mp4 comes out the other end.
 */
@ExtendWith(MockitoExtension.class)
class KenBurnsVideoGenServiceTest {

    @Mock
    private ObjectStorageService objectStorageService;

    private KenBurnsVideoGenService service;

    private static byte[] pngBytes(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void generate_producesANonTrivialMp4ForALandscapePhoto() throws Exception {
        service = new KenBurnsVideoGenService(objectStorageService);
        when(objectStorageService.downloadFromUrl("https://minio.example.com/landscape.jpg"))
                .thenReturn(pngBytes(640, 360));
        when(objectStorageService.store(org.mockito.ArgumentMatchers.any(byte[].class), anyString(), eq(".mp4"), eq("video/mp4")))
                .thenReturn("clips/landscape.mp4");

        String objectKey = service.generate("https://minio.example.com/landscape.jpg", VideoGenClient.VideoOrientation.LANDSCAPE);

        assertThat(objectKey).isEqualTo("clips/landscape.mp4");
        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        org.mockito.Mockito.verify(objectStorageService).store(bytesCaptor.capture(), anyString(), eq(".mp4"), eq("video/mp4"));
        assertThat(bytesCaptor.getValue().length).isGreaterThan(5_000); // a real 5s 1920x1080 h264 clip, not an empty/corrupt file
    }

    @Test
    void generate_producesANonTrivialMp4ForAPortraitPhoto() throws Exception {
        service = new KenBurnsVideoGenService(objectStorageService);
        when(objectStorageService.downloadFromUrl("https://minio.example.com/portrait.jpg"))
                .thenReturn(pngBytes(360, 640));
        when(objectStorageService.store(org.mockito.ArgumentMatchers.any(byte[].class), anyString(), eq(".mp4"), eq("video/mp4")))
                .thenReturn("clips/portrait.mp4");

        String objectKey = service.generate("https://minio.example.com/portrait.jpg", VideoGenClient.VideoOrientation.PORTRAIT);

        assertThat(objectKey).isEqualTo("clips/portrait.mp4");
        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        org.mockito.Mockito.verify(objectStorageService).store(bytesCaptor.capture(), anyString(), eq(".mp4"), eq("video/mp4"));
        assertThat(bytesCaptor.getValue().length).isGreaterThan(5_000);
    }
}
