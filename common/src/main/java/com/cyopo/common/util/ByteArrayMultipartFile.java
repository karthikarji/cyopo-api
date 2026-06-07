package com.cyopo.common.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Simple MultipartFile wrapper for in-memory byte arrays.
 * Use when you need to pass generated content (PDF, images etc.)
 * to services that expect MultipartFile without writing to disk.
 */
public class ByteArrayMultipartFile implements MultipartFile {

    private final byte[] bytes;
    private final String name;
    private final String contentType;

    public ByteArrayMultipartFile(byte[] bytes,
                                  String name,
                                  String contentType) {
        this.bytes = bytes;
        this.name = name;
        this.contentType = contentType;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getOriginalFilename() {
        return name;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return bytes.length == 0;
    }

    @Override
    public long getSize() {
        return bytes.length;
    }

    @Override
    public byte[] getBytes() {
        return bytes;
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public void transferTo(File dest) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(dest)) {
            fos.write(bytes);
        }
    }
}