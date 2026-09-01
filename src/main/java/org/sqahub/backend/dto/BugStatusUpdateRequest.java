package org.sqahub.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.sqahub.backend.model.BugStatus;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BugStatusUpdateRequest {

    @NotNull(message = "Status baru wajib diisi.")
    private BugStatus status;
}
