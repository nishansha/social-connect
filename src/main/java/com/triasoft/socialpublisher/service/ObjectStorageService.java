package com.triasoft.socialpublisher.service;

public interface ObjectStorageService {

    /** Downloads the bytes at a (possibly external, e.g. Replicate's delivery host) URL and stores them under this service's own bucket; returns the stored object key. */
    String storeFromUrl(String sourceUrl, String objectKeyPrefix);

    /** Stores raw bytes already in hand (e.g. a locally-produced ffmpeg output) under this service's own bucket; returns the stored object key. */
    String store(byte[] content, String objectKeyPrefix, String extension, String contentType);

    /** Raw bytes of an object already in this service's bucket — e.g. for VideoStitchService to pull clips down before running ffmpeg. */
    byte[] download(String objectKey);

    /** Short-lived, publicly-fetchable URL for an object already in this service's bucket — minted fresh per call, never cached. */
    String presignedUrl(String objectKey, int expirySeconds);
}
