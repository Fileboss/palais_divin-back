package com.pgu.palais_divin_back.business.service;

import com.pgu.palais_divin_back.business.config.MinIoConfig;
import com.pgu.palais_divin_back.business.exception.FileStorageException;
import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageServiceImpl implements FileStorageService {

    private final MinioClient minioClient;
    private final MinIoConfig minIoConfig;

    @PostConstruct
    private void init() {
        // Attendre que MinIO soit prêt
        int maxRetries = 10;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                // Test de connexion
                log.info("Tentative de connexion à MinIO (essai {}/{})", retryCount + 1, maxRetries);

                boolean exists = minioClient.bucketExists(
                        BucketExistsArgs.builder()
                                .bucket(minIoConfig.getBucketName())
                                .build()
                );

                if (!exists) {
                    minioClient.makeBucket(
                            MakeBucketArgs.builder()
                                    .bucket(minIoConfig.getBucketName())
                                    .build()
                    );
                    log.info("Bucket {} créé avec succès", minIoConfig.getBucketName());
                } else {
                    log.info("Bucket {} existe déjà", minIoConfig.getBucketName());
                }

                // IMPORTANT: Configurer l'accès public en lecture
                configureBucketPublicRead();

                log.info("Connexion à MinIO réussie et bucket configuré en accès public !");
                return;

            } catch (Exception e) {
                retryCount++;
                log.warn("Échec de la connexion à MinIO (essai {}/{}): {}", retryCount, maxRetries, e.getMessage());

                if (retryCount < maxRetries) {
                    try {
                        Thread.sleep(2000); // Attendre 2 secondes avant de réessayer
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new FileStorageException("Interruption lors de l'attente de MinIO", ie);
                    }
                } else {
                    log.error("Impossible de se connecter à MinIO après {} essais", maxRetries);
                    throw new FileStorageException("Impossible d'initialiser MinIO après " + maxRetries + " essais", e);
                }
            }
        }
    }

    /**
     * Configure le bucket pour autoriser l'accès public en lecture
     */
    private void configureBucketPublicRead() {
        try {
            // Politique JSON pour autoriser la lecture publique
            String policy = """
                {
                    "Version": "2012-10-17",
                    "Statement": [
                        {
                            "Effect": "Allow",
                            "Principal": {"AWS": "*"},
                            "Action": ["s3:GetObject"],
                            "Resource": ["arn:aws:s3:::%s/*"]
                        }
                    ]
                }
                """.formatted(minIoConfig.getBucketName());

            minioClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder()
                            .bucket(minIoConfig.getBucketName())
                            .config(policy)
                            .build()
            );

            log.info("Politique d'accès public configurée pour le bucket {}", minIoConfig.getBucketName());

        } catch (Exception e) {
            log.error("Erreur lors de la configuration de l'accès public pour le bucket", e);
            throw new FileStorageException("Impossible de configurer l'accès public", e);
        }
    }

    /**
     * Upload une photo de restaurant
     */
    @Override
    public String uploadRestaurantPhoto(String restaurantUuid, MultipartFile file) {
        validateFile(file);

        try {
            String fileName = generateFileName(restaurantUuid, file.getOriginalFilename());

            // Upload avec métadonnées pour un accès public optimal
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minIoConfig.getBucketName())
                            .object(fileName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            // Ajouter des headers pour le cache
                            .headers(Map.of(
                                    "Cache-Control", "public, max-age=31536000", // 1 an de cache
                                    "Content-Disposition", "inline" // Afficher dans le navigateur
                            ))
                            .build()
            );

            // Retourner l'URL publique directe
            String photoUrl = generatePublicPhotoUrl(fileName);
            log.info("Photo uploadée avec succès pour le restaurant {}: {}", restaurantUuid, photoUrl);
            return photoUrl;

        } catch (Exception e) {
            log.error("Erreur lors de l'upload de la photo pour le restaurant {}", restaurantUuid, e);
            throw new FileStorageException("Impossible d'uploader la photo", e);
        }
    }

    /**
     * Supprimer une photo de restaurant
     */
    @Override
    public void deleteRestaurantPhoto(String photoUrl) {
        try {
            String fileName = extractFileNameFromUrl(photoUrl);

            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minIoConfig.getBucketName())
                            .object(fileName)
                            .build()
            );

            log.info("Photo supprimée avec succès: {}", fileName);
        } catch (Exception e) {
            log.error("Erreur lors de la suppression de la photo: {}", photoUrl, e);
            throw new FileStorageException("Impossible de supprimer la photo", e);
        }
    }

    /**
     * Obtenir l'URL présignée (utile pour l'upload ou accès temporaire privé)
     */
    @Override
    public String getPresignedUrl(String fileName, int expireInMinutes) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minIoConfig.getBucketName())
                            .object(fileName)
                            .expiry(expireInMinutes, TimeUnit.MINUTES)
                            .build()
            );
        } catch (Exception e) {
            log.error("Erreur lors de la génération de l'URL présignée pour: {}", fileName, e);
            throw new FileStorageException("Impossible de générer l'URL présignée", e);
        }
    }

    /**
     * Vérifier si le bucket est configuré pour l'accès public
     */
    public boolean isBucketPublic() {
        try {
            String policy = minioClient.getBucketPolicy(
                    GetBucketPolicyArgs.builder()
                            .bucket(minIoConfig.getBucketName())
                            .build()
            );
            return policy != null && policy.contains("\"AWS\": \"*\"");
        } catch (Exception e) {
            log.warn("Impossible de vérifier la politique du bucket", e);
            return false;
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Le fichier ne peut pas être vide");
        }

        // Vérifier le type de fichier
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Seules les images sont autorisées");
        }

        // Vérifier la taille (max 5MB)
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("La taille du fichier ne peut pas dépasser 5MB");
        }
    }

    private String generateFileName(String restaurantUuid, String originalFileName) {
        String extension = getFileExtension(originalFileName);
        String timestamp = String.valueOf(System.currentTimeMillis());
        return String.format("restaurants/%s/%s%s", restaurantUuid, timestamp, extension);
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }

    /**
     * Génère l'URL publique directe (sans signature)
     */
    private String generatePublicPhotoUrl(String fileName) {
        return String.format("%s/%s/%s",
                minIoConfig.getEndpoint(),
                minIoConfig.getBucketName(),
                fileName);
    }

    private String extractFileNameFromUrl(String photoUrl) {
        // Extraire le nom du fichier depuis l'URL
        String prefix = String.format("%s/%s/", minIoConfig.getEndpoint(), minIoConfig.getBucketName());
        return photoUrl.replace(prefix, "");
    }
}