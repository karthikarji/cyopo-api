package com.cyopo.common.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.cyopo.common.exception.BadRequestException;
import com.cyopo.common.util.AppLogContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CloudinaryStorageService implements StorageService {

    private static final String CLASS = "CloudinaryStorageService";

    private final Cloudinary cloudinary;

    // ─── Upload MultipartFile ─────────────────────────────────────────

    /**
     * Uploads a MultipartFile to Cloudinary.
     * Delegates to doUpload() after reading bytes.
     *
     * @throws BadRequestException if file is null/empty or unreadable
     */
    @Override
    public StorageResult upload(MultipartFile file, StorageFolder folder) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required");
        }

        AppLogContext.info(CLASS, "upload",
                "Uploading file to Cloudinary",
                "fileName", file.getOriginalFilename(),
                "size", file.getSize(),
                "folder", folder.getPath());

        try {
            return doUpload(
                    file.getBytes(),
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getSize(),
                    folder);
        } catch (IOException e) {
            AppLogContext.error(CLASS, "upload",
                    "Failed to read file bytes before upload", e,
                    "fileName", file.getOriginalFilename());
            throw new BadRequestException(
                    "Failed to read file. Please try again.");
        }
    }

    // ─── Upload Raw Bytes ─────────────────────────────────────────────

    /**
     * Uploads raw bytes to Cloudinary.
     * Used for generated content (PDF invoices, base64 images)
     * that is already in memory without a MultipartFile wrapper.
     *
     * @throws BadRequestException if data is null or empty
     */
    @Override
    public StorageResult uploadBytes(byte[] data, String fileName,
                                     String mimeType, StorageFolder folder) {
        if (data == null || data.length == 0) {
            throw new BadRequestException("File data is required");
        }

        AppLogContext.info(CLASS, "uploadBytes",
                "Uploading bytes to Cloudinary",
                "fileName", fileName,
                "size", data.length,
                "mimeType", mimeType,
                "folder", folder.getPath());

        return doUpload(data, fileName, mimeType, data.length, folder);
    }

    // ─── Delete ───────────────────────────────────────────────────────

    /**
     * Deletes a file from Cloudinary by publicId.
     * Silently ignores null or blank publicIds.
     * Logs but does NOT throw on failure — deletion should never
     * block user-facing operations.
     */
    @Override
    public void delete(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            AppLogContext.debug(CLASS, "delete",
                    "Skipping delete — publicId is null or blank");
            return;
        }

        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            AppLogContext.info(CLASS, "delete",
                    "File deleted from Cloudinary",
                    "publicId", publicId);
        } catch (Exception e) {
            // Log as warn — deletion failure must not block the caller
            AppLogContext.warn(CLASS, "delete",
                    "Failed to delete from Cloudinary — asset may be orphaned",
                    "publicId", publicId,
                    "error", e.getMessage());
        }
    }

    // ─── Private — Core Upload ────────────────────────────────────────

    /**
     * Core upload method — all public methods delegate here.
     * Resolves resource_type from mimeType (image/video/raw).
     * Generates a unique publicId to prevent collisions.
     *
     * @throws BadRequestException if Cloudinary upload fails
     */
    @SuppressWarnings("unchecked")
    private StorageResult doUpload(byte[] data,
                                   String fileName,
                                   String mimeType,
                                   long fileSize,
                                   StorageFolder folder) {
        String publicId = folder.getPath() + "/"
                + UUID.randomUUID().toString().replace("-", "");

        String resourceType = resolveResourceType(mimeType);

        AppLogContext.debug(CLASS, "doUpload",
                "Starting Cloudinary upload",
                "publicId", publicId,
                "resourceType", resourceType,
                "mimeType", mimeType,
                "fileSize", fileSize);

        try {
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

            AppLogContext.info(CLASS, "doUpload",
                    "File uploaded successfully",
                    "publicId", uploadedId,
                    "url", url,
                    "fileSize", fileSize);

            return new StorageResult(uploadedId, url, fileSize);

        } catch (IOException e) {
            AppLogContext.error(CLASS, "doUpload",
                    "Cloudinary upload failed", e,
                    "publicId", publicId,
                    "resourceType", resourceType,
                    "fileSize", fileSize);
            throw new BadRequestException(
                    "File upload failed. Please try again.");
        }
    }

    // ─── Private — Resource Type ──────────────────────────────────────

    /**
     * Maps MIME type to Cloudinary resource_type.
     * image/* → image  (Cloudinary applies transformations)
     * video/* → video
     * other   → raw    (PDFs, Word docs, ZIPs etc.)
     */
    private String resolveResourceType(String mimeType) {
        if (mimeType == null) return "auto";
        if (mimeType.startsWith("image/")) return "image";
        if (mimeType.startsWith("video/")) return "video";
        return "raw";
    }
}