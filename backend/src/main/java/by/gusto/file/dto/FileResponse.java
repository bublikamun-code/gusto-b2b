package by.gusto.file.dto;

import by.gusto.file.entity.FileEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileResponse {

    private UUID id;
    private String storageKey;
    private String originalName;
    private String mimeType;
    private Long sizeBytes;
    private FileEntity.Visibility visibility;
    private String url;
    private Instant createdAt;
}
