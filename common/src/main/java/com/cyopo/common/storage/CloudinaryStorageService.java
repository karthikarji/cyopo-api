package com.cyopo.common.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.cyopo.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryStorageService implements StorageService {

    private final Cloudinary cloudinary;

    @Override
    public StorageResult upload(MultipartFile file, StorageFolder folder) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required");
        }
        try {
            return doUpload(file.getBytes(),
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getSize(),
                    folder);
        } catch (IOException e) {
            log.error("Failed to read file for upload: {}", e.getMessage());
            throw new BadRequestException(
                    "Failed to read file. Please try again.");
        }
    }

    @Override
    public StorageResult uploadBytes(byte[] data, String fileName,
                                     String mimeType, StorageFolder folder) {
        if (data == null || data.length == 0) {
            throw new BadRequestException("File data is required");
        }
        return doUpload(data, fileName, mimeType, data.length, folder);
    }

    @Override
    public void delete(String publicId) {
        if (publicId == null || publicId.isBlank()) return;
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            log.info("Deleted from Cloudinary: {}", publicId);
        } catch (Exception e) {
            // Log but do not throw — deletion failure should not block user
            log.warn("Failed to delete from Cloudinary [{}]: {}",
                    publicId, e.getMessage());
        }
    }

    // ─── Private ─────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private StorageResult doUpload(byte[] data, String fileName,
                                   String mimeType, long fileSize,
                                   StorageFolder folder) {
        try {
            String publicId = folder.getPath() + "/"
                    + UUID.randomUUID().toString().replace("-", "");

            String resourceType = resolveResourceType(mimeType);

            Map<String, Object> params = ObjectUtils.asMap(
                    "public_id", publicId,
                    "resource_type", resourceType,
                    "overwrite", true,
                    "folder", folder.getPath()
            );

            Map<String, Object> result = cloudinary.uploader()
                    .upload(data, params);

            String url = (String) result.get("secure_url");
            String uploadedId = (String) result.get("public_id");

            log.info("Uploaded to Cloudinary: {} → {}", uploadedId, url);

            return new StorageResult(uploadedId, url, fileSize);

        } catch (IOException e) {
            log.error("Cloudinary upload failed: {}", e.getMessage());
            throw new BadRequestException(
                    "File upload failed. Please try again.");
        }
    }

    private String resolveResourceType(String mimeType) {
        if (mimeType == null) return "auto";
        if (mimeType.startsWith("image/")) return "image";
        if (mimeType.startsWith("video/")) return "video";
        return "raw"; // PDFs, Word docs, etc.
    }
}