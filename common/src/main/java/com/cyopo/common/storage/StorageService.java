package com.cyopo.common.storage;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    /**
     * Upload a file to cloud storage.
     * @param file     the multipart file to upload
     * @param folder   the logical folder (PROFILES, RESUMES, THUMBNAILS)
     * @return StorageResult containing publicId, url, fileSize
     */
    StorageResult upload(MultipartFile file, StorageFolder folder);

    /**
     * Upload raw bytes to cloud storage.
     * Used for base64 profile photos already in memory.
     */
    StorageResult uploadBytes(byte[] data, String fileName,
                              String mimeType, StorageFolder folder);

    /**
     * Delete a file from cloud storage by its publicId.
     * Silently ignores if publicId is null or not found.
     */
    void delete(String publicId);
}