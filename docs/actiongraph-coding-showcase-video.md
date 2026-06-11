# ActionGraph Coding Showcase Video

This video is a long-form technical showcase for ActionGraph adoption.
It is designed for engineers who need to understand how the framework is used in real code.

Output files:

- `docs/assets/actiongraph-coding-showcase.mp4`
- `docs/assets/actiongraph-coding-showcase-poster.png`

Source material:

- `docs/examples/actiongraph-coding-showcase/build.gradle.kts`
- `docs/examples/actiongraph-coding-showcase/src/main/resources/application-actiongraph.yml`
- `docs/examples/actiongraph-coding-showcase/src/main/java/demo/OrderCancellationWorkflow.java`
- `docs/examples/actiongraph-coding-showcase/src/main/java/demo/CancellationRunService.java`

Narrative structure:

1. Show the business problem: ordinary service calls lack planning, review, recovery, and audit evidence.
2. Add the ActionGraph BOM and Spring Boot starter.
3. Write the first annotated Action using the real `ActionGraphAction` API.
4. Add more Actions and show the business path forming from preconditions and effects.
5. Add `ActionGraphGuard` and `ActionGraphCompensation`.
6. Mark a high-risk Action with `requiresHumanReview = true`.
7. Enable runtime capabilities through focused `actiongraph.*` configuration.
8. Start a run from a business service by submitting a goal and initial facts.
9. Approve and resume through the real control-plane client style.
10. Show Console trace and JSONL audit export as visual evidence, not terminal output.

Design notes:

- The video is rendered frame by frame with Pillow and ffmpeg, not captured from HTML.
- Runtime effects are shown as Chinese dashboards, timelines, approval cards, and audit evidence.
- Code appears through a typewriter-style IDE animation so the framework usage is concrete rather than described only in prose.
