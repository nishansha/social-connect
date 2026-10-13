package com.triasoft.socialpublisher.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OAuthPageOptionRs {
    private String pageId;
    private String pageName;
    /** Null if this Page has no linked Instagram Business account — can't post to Instagram with it. */
    private String igBusinessAccountId;
}
