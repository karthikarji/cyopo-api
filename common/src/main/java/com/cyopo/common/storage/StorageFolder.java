package com.cyopo.common.storage;

public enum StorageFolder {
    PROFILES("cyopo/profiles"),
    RESUMES("cyopo/resumes"),
    THUMBNAILS("cyopo/thumbnails"),
    INVOICES("cyopo/invoices");

    private final String path;

    StorageFolder(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}