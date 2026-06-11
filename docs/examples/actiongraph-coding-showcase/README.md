# ActionGraph Coding Showcase

This folder contains the source snippets used by the long-form coding showcase video.
The scenario is an order-cancellation workflow implemented as ordinary Spring methods
and upgraded into governed ActionGraph actions.

Files:

- `build.gradle.kts` adds the BOM and Spring Boot starter.
- `src/main/resources/application-actiongraph.yml` enables the runtime, validation, persistence, human review, masking, amount limits, and Console surfaces.
- `src/main/java/demo/OrderCancellationWorkflow.java` shows the annotated business workflow.
- `src/main/java/demo/CancellationRunService.java` shows a programmatic run entry point using the real planner/executor types.

The snippets are presentation-sized, but the framework imports and property names match the repository source.
