package com.pgu.palais_divin_back.business.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

// DTO pour les réponses d'upload
@Data
@AllArgsConstructor
public class FileUploadResponseDto {
    private String message;
    private String fileName;
    private String photoUrl;
    private long size;
    private String contentType;
}
