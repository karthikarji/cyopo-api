package com.cyopo.common.storage;

public record StorageResult(
        String publicId,
        String url,
        long   fileSize
) {}