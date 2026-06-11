# ActionGraph Executive Tutorial Video

## Creative Direction

This is a dynamic executive-facing explainer video, not a webpage recording and not a developer tutorial. The video translates the transfer-demo journey into business language: one sentence starts a process, enterprise rules keep it safe, risky work pauses for approval, and audit evidence is ready when leadership or compliance asks for it.

- Format: 16:9 MP4, 1280 x 720, 24 fps
- Duration: about 76 seconds
- Tone: confident internal promotion film, business-first, easy for non-technical leaders to understand
- Visual language: ActionGraph green, blue execution paths, amber review states, coral policy stops
- Output: `docs/assets/actiongraph-executive-demo.mp4`
- Poster: `docs/assets/actiongraph-executive-demo-poster.png`

## Storyboard

| Time | Section | Motion Design | Message |
| --- | --- | --- | --- |
| 00-08s | Opening | Logo mark resolves into a governed action graph; an execution beam lights up across business nodes. | AI should not only answer questions; it should help finish work safely. |
| 08-18s | Business Request | A leader's one-sentence request enters the system; customer, amount, and task cards appear. | People speak naturally. The system extracts what matters and asks when information is missing. |
| 18-28s | Safe Route | The process map appears with permission, amount, and approval gates. An unauthorized shortcut is blocked. | AI can only walk the routes the enterprise has approved. |
| 28-42s | Auto Reroute | Realtime balance service flashes unstable; the execution token reroutes to a snapshot path without manual orchestration. | When systems fail, the process can degrade gracefully instead of waiting for firefighting. |
| 42-55s | Managed Approval | 800k crosses the review line; reviewer cards slide in with status. | Large or risky work pauses, routes to the right approvers, and resumes from the same case. |
| 55-66s | Audit Evidence | A timeline becomes linked evidence blocks; sensitive fields are masked. | Compliance evidence is generated during the process, not patched together afterward. |
| 66-76s | Business Value | A 6m request hits a hard-limit gate; final value cards land: efficiency, risk control, audit, legacy integration. | The business keeps final authority while AI becomes operationally useful. |

## Narration Script

AI should not stop at answering questions. In enterprise systems, the real value is helping people finish work safely.

With ActionGraph, a colleague can describe the business outcome in one sentence. The system extracts the customer, amount, and task, and asks for missing information instead of guessing.

From there, the work only moves through approved business routes. Permissions, amount rules, review requirements, compensation, and audit are built into the execution path.

If a core service is unstable, the run can switch to an approved fallback path. The business flow continues, but the evidence records exactly what happened.

When risk goes up, ActionGraph stops and asks the right people. Reviewers can see why a case is waiting, approve each stage, and continue the same case after approval.

Every step produces trace evidence. Sensitive information is masked for display, while the audit chain proves whether the record was changed.

And when a request crosses the hard boundary, the system stops before a business side effect is created.

That is the difference between an AI demo and an AI execution platform.

## Production Notes

- The video is generated with `tools/render_actiongraph_tutorial_video.py`.
- The script draws frames with Pillow and encodes MP4 with `imageio-ffmpeg`.
- It intentionally avoids HTML, DOM screenshots, browser capture, or static slide export.
- Text on screen is business-oriented; framework terms are kept in supporting labels only when they help explain governance.
