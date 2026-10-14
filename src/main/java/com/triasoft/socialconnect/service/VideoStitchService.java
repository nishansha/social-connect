package com.triasoft.socialconnect.service;

import java.util.List;

public interface VideoStitchService {

    /** Downloads each clip (in order), concatenates them with ffmpeg, uploads and returns the combined object key. */
    String stitch(List<String> clipObjectKeysInOrder);
}
