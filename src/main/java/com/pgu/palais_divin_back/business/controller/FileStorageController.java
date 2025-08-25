package com.pgu.palais_divin_back.business.controller;

import com.pgu.palais_divin_back.business.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@Slf4j
public class FileStorageController {

    private final FileStorageService fileStorageService;

    @GetMapping("/presigned-url")
    public ResponseEntity<Map<String, String>> getPresignedUrl(
            @RequestParam String fileName,
            @RequestParam(defaultValue = "60") int expireInMinutes) {
        try {
            String presignedUrl = fileStorageService.getPresignedUrl(fileName, expireInMinutes);
            Map<String, String> response = Map.of("presignedUrl", presignedUrl);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Erreur lors de la génération de l'URL présignée", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam String restaurantId) {
        try {
            String photoUrl = fileStorageService.uploadRestaurantPhoto(restaurantId, file);
            Map<String, String> response = Map.of(
                    "message", "Fichier uploadé avec succès",
                    "photoUrl", photoUrl
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = Map.of("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Erreur lors de l'upload", e);
            Map<String, String> error = Map.of("error", "Erreur interne du serveur");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}

