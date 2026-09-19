package by.gusto.integration.dto;

import java.util.List;
import java.util.UUID;

public record ImportReport(
        UUID integrationFileId,
        String type,
        String status,
        int rowsTotal,
        int rowsOk,
        int rowsError,
        List<RowError> errors) {

    public record RowError(int row, String message) {
    }
}
