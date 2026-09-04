package com.ai.cs.common.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RagSearchResultDTO {
    public static final String HIT = "HIT";
    public static final String MISS = "MISS";
    public static final String UNAVAILABLE = "UNAVAILABLE";

    /** HIT / MISS / UNAVAILABLE */
    private String status;
    private String reply;
    private List<RagCitationDTO> citations = new ArrayList<>();
    private String error;

    public static RagSearchResultDTO hit(String reply, List<RagCitationDTO> citations) {
        RagSearchResultDTO dto = new RagSearchResultDTO();
        dto.setStatus(HIT);
        dto.setReply(reply);
        dto.setCitations(citations == null ? new ArrayList<>() : citations);
        return dto;
    }

    public static RagSearchResultDTO miss(String reply) {
        RagSearchResultDTO dto = new RagSearchResultDTO();
        dto.setStatus(MISS);
        dto.setReply(reply);
        return dto;
    }

    public static RagSearchResultDTO unavailable(String error) {
        RagSearchResultDTO dto = new RagSearchResultDTO();
        dto.setStatus(UNAVAILABLE);
        dto.setError(error);
        return dto;
    }
}
