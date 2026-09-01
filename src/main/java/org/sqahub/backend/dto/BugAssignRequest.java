package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BugAssignRequest {

    // Null berarti UN-assign (lepas dari siapa pun).
    private Long assignedToUserId;
}
