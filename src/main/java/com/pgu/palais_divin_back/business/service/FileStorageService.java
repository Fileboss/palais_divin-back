package com.pgu.palais_divin_back.business.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    String uploadRestaurantPhoto(String restaurantUuid, MultipartFile file);

    void deleteRestaurantPhoto(String photoUrl);

    String getPresignedUrl(String fileName, int expireInMinutes);
}
