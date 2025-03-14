package nimble.workflow.model;

import org.jdbi.v3.core.mapper.reflect.ColumnName;

import java.time.Instant;

public record Workflow(
        String id,
        @ColumnName("class_name") String className,
        Object input,
        Object output,
        Instant created,
        WorkflowEvent scheduled,
        WorkflowEvent started) {
}
