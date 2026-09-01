package by.gusto.file.dto;

import by.gusto.file.entity.FileEntity;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileUploadRequest {

    @NotNull
    private FileEntity.Visibility visibility;
}
