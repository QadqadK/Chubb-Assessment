package com.chubb.claims.service;

import com.chubb.claims.api.error.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** Local filesystem storage for claim documents. Metadata lives in PostgreSQL. */
@Component
public class DocumentStorage {

    private static final long MAX_BYTES = 10L * 1024 * 1024;

    private final Path root;

    public DocumentStorage(@Value("${app.storage.upload-dir}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public StoredFile store(UUID claimId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("A non-empty file is required");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("File exceeds the 10 MB limit");
        }

        String fileName = sanitise(file.getOriginalFilename());
        String contentType = file.getContentType() == null
                ? "application/octet-stream"
                : file.getContentType();
        Path directory = root.resolve(claimId.toString());

        try {
            Files.createDirectories(directory);
            String storedName = UUID.randomUUID() + "-" + fileName;
            Path target = directory.resolve(storedName).normalize();
            if (!target.startsWith(directory)) {
                throw new BadRequestException("Invalid file name");
            }
            try (var input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            String relativePath = claimId + "/" + storedName;
            return new StoredFile(fileName, relativePath, contentType, file.getSize());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store document", ex);
        }
    }

    private static String sanitise(String original) {
        String raw = (original == null || original.isBlank()) ? "upload" : original;
        String base = raw.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        base = base.replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.isBlank()) {
            base = "upload";
        }
        if (base.length() > 200) {
            base = base.substring(base.length() - 200);
        }
        return base;
    }

    public record StoredFile(String fileName, String storagePath, String contentType, long sizeBytes) {
    }
}
