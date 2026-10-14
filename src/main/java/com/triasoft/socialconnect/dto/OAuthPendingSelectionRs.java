package com.triasoft.socialconnect.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class OAuthPendingSelectionRs {
    private String stateToken;
    private List<OAuthPageOptionRs> pages;
}
