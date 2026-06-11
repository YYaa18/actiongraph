#!/usr/bin/env python3
"""Render a long-form ActionGraph coding showcase video.

The video is frame-rendered with Pillow and ffmpeg. It shows real framework
imports, annotations, configuration keys, and runtime API concepts without
using a terminal-style output panel for the execution result.
"""

from __future__ import annotations

import argparse
import math
import textwrap
from pathlib import Path
from typing import Sequence

try:
    import imageio.v2 as imageio
except Exception as exc:  # pragma: no cover - user-facing dependency check
    raise SystemExit(
        "Missing imageio/imageio-ffmpeg. Install them into a temporary path, for example:\n"
        "python3 -m pip install --target /tmp/codex-video-deps imageio imageio-ffmpeg\n"
        "PYTHONPATH=/tmp/codex-video-deps python3 tools/render_actiongraph_coding_showcase_video.py"
    ) from exc

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from render_actiongraph_tutorial_video import (
    AMBER,
    AMBER_SOFT,
    BLUE,
    BLUE_SOFT,
    CODE_BG,
    CORAL,
    CORAL_SOFT,
    FAINT,
    FONTS,
    FPS,
    GREEN,
    GREEN_DARK,
    GREEN_SOFT,
    H,
    INK,
    INK_2,
    LINE,
    MUTED,
    WHITE,
    arrow,
    card,
    clamp,
    draw_logo_mark,
    draw_node,
    draw_text,
    ease,
    ease_out,
    line_path,
    mix,
    rounded,
    text_bbox,
    with_alpha,
    W,
)


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT = ROOT / "docs/assets/actiongraph-coding-showcase.mp4"
DEFAULT_POSTER = ROOT / "docs/assets/actiongraph-coding-showcase-poster.png"
WIDE_BLUE = (28, 88, 190)
DARK = (13, 21, 18)
PANEL = (20, 31, 26)
PANEL_2 = (27, 42, 35)
GRID = (209, 222, 216)
PURPLE = (109, 92, 220)
PURPLE_SOFT = (239, 236, 255)
CYAN = (26, 159, 196)
CYAN_SOFT = (226, 247, 252)
RED = (205, 67, 67)
RED_SOFT = (255, 238, 237)
DURATION = 300.0

SCENES = [
    (0.0, 20.0, "场景目标"),
    (20.0, 48.0, "接入依赖"),
    (48.0, 78.0, "第一个 Action"),
    (78.0, 112.0, "路径组合"),
    (112.0, 142.0, "Guard 与补偿"),
    (142.0, 172.0, "人审 Action"),
    (172.0, 204.0, "配置策略"),
    (204.0, 238.0, "发起运行"),
    (238.0, 266.0, "审批恢复"),
    (266.0, 288.0, "审计看板"),
    (288.0, 300.0, "总结"),
]


def code_font(size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = [
        "/System/Library/Fonts/Menlo.ttc",
        "/System/Library/Fonts/Monaco.ttf",
        "/Library/Fonts/Menlo.ttc",
        "/System/Library/Fonts/Supplemental/Courier New.ttf",
    ]
    for candidate in candidates:
        if Path(candidate).exists():
            return ImageFont.truetype(candidate, size=size)
    return ImageFont.load_default()


CODE_FONTS = {
    "body": code_font(13),
    "small": code_font(12),
    "tiny": code_font(10),
}


GRADLE_CODE = textwrap.dedent(
    """
    plugins {
        id("org.springframework.boot") version "3.3.5"
        id("io.spring.dependency-management") version "1.1.6"
        java
    }

    dependencies {
        implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
        implementation("com.actiongraph:actiongraph-spring-boot-starter")
        runtimeOnly("org.postgresql:postgresql")
    }
    """
).strip()

WORKFLOW_IMPORTS_CODE = textwrap.dedent(
    """
    package demo;

    import com.actiongraph.action.ActionRiskLevel;
    import com.actiongraph.action.annotation.ActionGraphAction;
    import com.actiongraph.action.annotation.ActionGraphCompensation;
    import com.actiongraph.action.annotation.ActionGraphGuard;
    import org.springframework.stereotype.Service;

    @Service
    final class OrderCancellationWorkflow {
        private final OrderService orderService;
        private final CancellationPolicyService policyService;
        private final CancellationRequestService requestService;
        private final OperationsApprovalService approvalService;

        @ActionGraphAction(
                id = "order.lookup",
                preconditions = "order-cancellation:ORDER_ID_PRESENT",
                effects = "order-cancellation:ORDER_LOADED",
                riskLevel = ActionRiskLevel.READ_ONLY
        )
        OrderRecord lookup(OrderId orderId) {
            return orderService.find(orderId);
        }
    }
    """
).strip()

WORKFLOW_PATH_CODE = textwrap.dedent(
    """
    @ActionGraphAction(
            id = "order.cancellation.eligibility.check",
            preconditions = "order-cancellation:ORDER_LOADED",
            effects = "order-cancellation:CANCELLATION_ELIGIBILITY_CHECKED"
    )
    CancellationEligibility check(OrderRecord order) {
        return policyService.check(order);
    }

    @ActionGraphAction(
            id = "order.cancellation.request.draft",
            preconditions = {
                    "order-cancellation:ORDER_LOADED",
                    "order-cancellation:CANCELLATION_ELIGIBILITY_CHECKED"
            },
            effects = "order-cancellation:CANCELLATION_REQUEST_DRAFTED",
            riskLevel = ActionRiskLevel.MEDIUM,
            maxAttempts = 2,
            backoffMillis = 250
    )
    CancellationRequestDraft draft(OrderRecord order, CancellationEligibility eligibility) {
        return requestService.createDraft(order, eligibility);
    }
    """
).strip()

GUARD_CODE = textwrap.dedent(
    """
    @ActionGraphGuard(actionId = "order.cancellation.request.draft")
    boolean canDraft(CancellationEligibility eligibility) {
        return eligibility.eligible();
    }

    @ActionGraphCompensation(actionId = "order.cancellation.request.draft")
    void voidDraft(CancellationRequestDraft draft) {
        requestService.voidDraft(draft.requestId());
    }
    """
).strip()

REVIEW_CODE = textwrap.dedent(
    """
    @ActionGraphAction(
            id = "operations.approval.request",
            preconditions = "order-cancellation:CANCELLATION_REQUEST_DRAFTED",
            effects = "order-cancellation:OPERATIONS_APPROVAL_REQUESTED",
            riskLevel = ActionRiskLevel.HIGH,
            requiresHumanReview = true
    )
    OperationsApprovalRequest requestApproval(CancellationRequestDraft draft) {
        return approvalService.requestApproval(draft);
    }
    """
).strip()

CONFIG_CODE = textwrap.dedent(
    """
    actiongraph:
      actions:
        auto-register-annotated: true
      validation:
        mode: FAIL
      planner:
        max-depth: 16
      executor:
        max-steps: 32
      execution:
        policies:
          - action-id: order.cancellation.request.draft
            max-attempts: 2
            backoff: 250ms
            timeout: 2s
      runtime:
        api:
          enabled: true
          path: /actiongraph/runtime
      persistence:
        jdbc:
          enabled: true
          suspended-run-claim-timeout: 15m
      human-review:
        api:
          enabled: true
        callback-endpoint:
          enabled: true
        risk-based-approval-chain: true
      masking:
        enabled: true
      limits:
        rules:
          - action-id: operations.approval.request
            currency: CNY
            review-limit: 5000
            hard-limit: 50000
      console:
        enabled: true
    """
).strip()

RUN_CODE = textwrap.dedent(
    """
    @Service
    final class CancellationRunService {
        private static final Condition ORDER_ID_PRESENT =
                Condition.of("order-cancellation", "ORDER_ID_PRESENT");
        private static final Condition APPROVAL_REQUESTED =
                Condition.of("order-cancellation", "OPERATIONS_APPROVAL_REQUESTED");

        private final Executor executor;
        private final ActionRegistry registry;

        RunResult requestCancellation(String orderId) {
            InMemoryBlackboard blackboard = new InMemoryBlackboard();
            blackboard.put(new OrderId(orderId));
            blackboard.addCondition(ORDER_ID_PRESENT);

            return executor.run(
                    new Goal("requestOrderCancellation", Set.of(APPROVAL_REQUESTED)),
                    blackboard,
                    registry.all(),
                    registry
            );
        }
    }
    """
).strip()

REVIEW_CLIENT_CODE = textwrap.dedent(
    """
    ControlPlaneHttpResponse approved =
            client.humanReview().decide(
            runId,
            "operations.approval.request",
            Integer.valueOf(0),
            "APPROVED",
            "ops-lead",
            "Risk reviewed",
            requestHeaders
    );
    requireSuccessful("human review", approved);

    ControlPlaneHttpResponse resumed =
            client.runtime().resume(runId, requestHeaders);
    requireSuccessful("runtime resume", resumed);
    """
).strip()

CONSOLE_CODE = textwrap.dedent(
    """
    ControlPlaneHttpResponse trace =
            client.console().trace(runId, requestHeaders);

    ControlPlaneHttpResponse jsonl =
            client.console().traceJsonl(runId, requestHeaders);

    requireSuccessful("trace", trace);
    requireSuccessful("trace export", jsonl);
    """
).strip()


def scene_bounds(t: float) -> tuple[int, float, str]:
    for i, (start, end, label) in enumerate(SCENES):
        if start <= t < end:
            return i, (t - start) / (end - start), label
    return len(SCENES) - 1, 1.0, SCENES[-1][2]


def background(t: float) -> Image.Image:
    img = Image.new("RGB", (1280, 720), (244, 249, 247))
    draw = ImageDraw.Draw(img, "RGBA")
    for y in range(H):
        p = y / H
        color = mix((247, 252, 250), (234, 244, 241), p)
        draw.line((0, y, W, y), fill=color)
    for x in range(0, W, 64):
        draw.line((x, 0, x, H), fill=(*GRID, 115), width=1)
    for y in range(0, H, 64):
        draw.line((0, y, W, y), fill=(*GRID, 115), width=1)
    for i in range(24):
        x = (55 + i * 83 + math.sin(t * 0.35 + i * 0.7) * 24) % W
        y = 95 + ((i * 49 + t * 18) % 545)
        color = [GREEN, BLUE, AMBER, PURPLE, CYAN][i % 5]
        alpha = int(18 + 28 * (0.5 + 0.5 * math.sin(t * 0.8 + i)))
        draw.ellipse((x - 3, y - 3, x + 3, y + 3), fill=(*color, alpha))
    return img


def header(draw: ImageDraw.ImageDraw, t: float, label: str) -> None:
    draw_logo_mark(draw, 36, 24, 44)
    draw.text((92, 29), "ActionGraph", font=FONTS["h3"], fill=INK)
    draw.text((94, 58), "真实代码使用演示", font=FONTS["tiny"], fill=MUTED)
    rounded(draw, (960, 31, 1220, 64), 15, (255, 255, 255, 225), LINE)
    draw.text((982, 41), label, font=FONTS["tiny"], fill=GREEN_DARK)
    draw.rounded_rectangle((38, 690, 1242, 698), radius=4, fill=(220, 230, 225))
    draw.rounded_rectangle((38, 690, 38 + int(1204 * clamp(t / DURATION)), 698), radius=4, fill=GREEN)


def section_title(draw: ImageDraw.ImageDraw, no: str, title: str, subtitle: str) -> None:
    draw.text((58, 100), no, font=FONTS["h2"], fill=GREEN_DARK)
    draw.text((115, 96), title, font=FONTS["h1"], fill=INK)
    draw_text(draw, (118, 148), subtitle, FONTS["body"], MUTED, max_width=1050, line_gap=6)


def fit_code(draw: ImageDraw.ImageDraw, text: str, font: ImageFont.ImageFont, max_width: int) -> str:
    if text_bbox(draw, text, font)[0] <= max_width:
        return text
    clipped = text
    while len(clipped) > 4 and text_bbox(draw, clipped + "...", font)[0] > max_width:
        clipped = clipped[:-1]
    return clipped + "..."


def code_color(line: str) -> tuple[int, int, int]:
    stripped = line.strip()
    if stripped.startswith("@"):
        return (126, 232, 157)
    if stripped.startswith("import") or stripped.startswith("package"):
        return (142, 196, 255)
    if stripped.startswith(("public", "final", "record", "interface", "class", "@Service")):
        return (238, 213, 133)
    if '"' in line:
        return (255, 211, 137)
    if stripped.startswith(("return", "new", "private", "static")):
        return (210, 230, 221)
    return (232, 243, 238)


def draw_file_tree(draw: ImageDraw.ImageDraw, x: int, y: int, h: int, current: str) -> None:
    rounded(draw, (x, y, x + 138, y + h), 7, (16, 27, 22), None)
    draw.text((x + 16, y + 17), "项目文件", font=FONTS["tiny"], fill=(184, 202, 193))
    files = [
        "build.gradle.kts",
        "application.yml",
        "Workflow.java",
        "RunService.java",
        "ReviewClient.java",
        "ConsoleClient.java",
    ]
    yy = y + 54
    for name in files:
        active = name == current
        fill = (34, 67, 48) if active else (16, 27, 22)
        rounded(draw, (x + 10, yy, x + 128, yy + 30), 5, fill, None)
        dot = GREEN if active else (85, 103, 94)
        draw.ellipse((x + 19, yy + 12, x + 27, yy + 20), fill=dot)
        draw.text((x + 35, yy + 8), name, font=CODE_FONTS["tiny"], fill=(224, 238, 231) if active else (139, 157, 148))
        yy += 34


def draw_ide(
    draw: ImageDraw.ImageDraw,
    box: tuple[int, int, int, int],
    file_name: str,
    code: str,
    progress: float,
    active_lines: Sequence[int] = (),
    current_tree: str | None = None,
    title: str = "IDE",
) -> None:
    x1, y1, x2, y2 = box
    rounded(draw, box, 10, DARK, None)
    draw.rectangle((x1, y1, x2, y1 + 38), fill=(17, 28, 23))
    for i, c in enumerate([(242, 105, 93), (244, 190, 79), (77, 202, 124)]):
        draw.ellipse((x1 + 16 + i * 18, y1 + 14, x1 + 26 + i * 18, y1 + 24), fill=c)
    draw.text((x1 + 88, y1 + 12), title, font=FONTS["tiny"], fill=(202, 221, 211))
    current = current_tree or file_name
    draw_file_tree(draw, x1 + 10, y1 + 48, y2 - y1 - 60, current)
    code_x = x1 + 158
    code_y = y1 + 50
    code_w = x2 - code_x - 16
    rounded(draw, (code_x, code_y, x2 - 12, y2 - 12), 7, PANEL, (39, 57, 48))
    rounded(draw, (code_x, code_y, x2 - 12, code_y + 34), 7, PANEL_2, None)
    draw.text((code_x + 14, code_y + 10), file_name, font=CODE_FONTS["small"], fill=(218, 234, 226))

    typed = code[: int(len(code) * ease_out(progress))]
    lines = typed.split("\n") if typed else [""]
    line_h = 19
    max_lines = max(1, (y2 - code_y - 64) // line_h)
    start = max(0, len(lines) - max_lines)
    visible = lines[start:]
    yy = code_y + 48
    for idx, line in enumerate(visible, start=start + 1):
        if idx in active_lines:
            rounded(draw, (code_x + 42, yy - 3, x2 - 24, yy + 18), 4, (38, 70, 50), None)
        draw.text((code_x + 12, yy), f"{idx:>2}", font=CODE_FONTS["tiny"], fill=(104, 126, 115))
        clipped = fit_code(draw, line, CODE_FONTS["body"], code_w - 64)
        draw.text((code_x + 48, yy), clipped, font=CODE_FONTS["body"], fill=code_color(line))
        yy += line_h
    if progress < 1.0:
        last = visible[-1] if visible else ""
        cursor_x = code_x + 48 + text_bbox(draw, fit_code(draw, last, CODE_FONTS["body"], code_w - 64), CODE_FONTS["body"])[0] + 2
        cursor_y = yy - line_h
        if int(progress * 20) % 2 == 0:
            draw.rectangle((cursor_x, cursor_y + 1, cursor_x + 2, cursor_y + 18), fill=(126, 232, 157))


def capability_card(
    draw: ImageDraw.ImageDraw,
    box: tuple[int, int, int, int],
    title: str,
    body: str,
    color: tuple[int, int, int],
    active: bool = True,
) -> None:
    fill = {
        GREEN: GREEN_SOFT,
        BLUE: BLUE_SOFT,
        AMBER: AMBER_SOFT,
        CORAL: CORAL_SOFT,
        PURPLE: PURPLE_SOFT,
        CYAN: CYAN_SOFT,
        RED: RED_SOFT,
    }.get(color, GREEN_SOFT)
    card(draw, box, mix(WHITE, fill, 0.85 if active else 0.2), color if active else LINE)
    draw.text((box[0] + 16, box[1] + 14), title, font=FONTS["h3"], fill=INK if active else MUTED)
    draw_text(draw, (box[0] + 16, box[1] + 48), body, FONTS["small"], INK_2 if active else MUTED, max_width=box[2] - box[0] - 30, line_gap=5)


def draw_module_stack(draw: ImageDraw.ImageDraw, p: float) -> None:
    card(draw, (770, 230, 1190, 602), WHITE, LINE)
    draw.text((796, 255), "接入后获得的能力", font=FONTS["h2"], fill=INK)
    rows = [
        ("BOM 对齐版本", "所有模块版本一致", GREEN),
        ("Spring Boot Starter", "扫描注解并注册 Action", BLUE),
        ("JDBC Repository", "Trace / 挂起运行可持久化", AMBER),
        ("Human Review", "高风险动作进入审批链", PURPLE),
        ("Console", "只读运行看板和审计导出", CYAN),
    ]
    yy = 312
    for i, (title, body, color) in enumerate(rows):
        active = p > 0.12 + i * 0.13
        rounded(draw, (800, yy, 1160, yy + 46), 8, mix(WHITE, GREEN_SOFT if color == GREEN else BLUE_SOFT if color == BLUE else AMBER_SOFT if color == AMBER else PURPLE_SOFT if color == PURPLE else CYAN_SOFT, 0.75 if active else 0.12), color if active else LINE)
        draw.text((818, yy + 9), title, font=FONTS["small"], fill=INK if active else MUTED)
        draw.text((980, yy + 9), body, font=FONTS["tiny"], fill=INK_2 if active else MUTED)
        yy += 54


def draw_action_graph(draw: ImageDraw.ImageDraw, box: tuple[int, int, int, int], active: int, token_p: float = 0.0) -> None:
    x1, y1, x2, y2 = box
    card(draw, box, WHITE, LINE)
    draw.text((x1 + 22, y1 + 18), "规划器看到的业务图", font=FONTS["h3"], fill=INK)
    nodes = [
        ((x1 + 58, y1 + 140), "订单编号", GREEN),
        ((x1 + 138, y1 + 140), "查询订单", BLUE),
        ((x1 + 220, y1 + 140), "资格校验", BLUE),
        ((x1 + 302, y1 + 140), "创建草稿", AMBER),
        ((x1 + 382, y1 + 140), "运营审批", PURPLE),
    ]
    pts = [xy for xy, _, _ in nodes]
    line_path(draw, pts, LINE, 5, 230)
    for i in range(max(0, min(active, len(pts) - 1))):
        arrow(draw, pts[i], pts[i + 1], GREEN if i < 2 else AMBER if i == 2 else PURPLE, 4)
    for i, (xy, label, color) in enumerate(nodes):
        draw_node(draw, xy, label, active=i <= active, color=color, scale=0.78)
    if active > 0:
        px, py = point_on_polyline(pts, token_p)
        draw.ellipse((px - 9, py - 9, px + 9, py + 9), fill=(255, 255, 255), outline=GREEN, width=3)
    facts = [
        ("ORDER_LOADED", active >= 1, BLUE),
        ("ELIGIBILITY_CHECKED", active >= 2, BLUE),
        ("DRAFTED", active >= 3, AMBER),
        ("APPROVAL_REQUESTED", active >= 4, PURPLE),
    ]
    for i, (text, ok, color) in enumerate(facts):
        col = i % 2
        row = i // 2
        xx = x1 + 28 + col * 190
        yy = y1 + 250 + row * 44
        rounded(draw, (xx, yy, xx + 176, yy + 34), 17, mix(WHITE, GREEN_SOFT if color == GREEN else BLUE_SOFT if color == BLUE else AMBER_SOFT if color == AMBER else PURPLE_SOFT, 0.8 if ok else 0.1), color if ok else LINE)
        draw.text((xx + 14, yy + 9), text, font=CODE_FONTS["tiny"], fill=INK_2 if ok else MUTED)


def point_on_polyline(points: Sequence[tuple[float, float]], p: float) -> tuple[float, float]:
    p = clamp(p)
    lengths: list[float] = []
    total = 0.0
    for a, b in zip(points, points[1:]):
        seg = math.hypot(b[0] - a[0], b[1] - a[1])
        lengths.append(seg)
        total += seg
    target = total * p
    seen = 0.0
    for (a, b), seg in zip(zip(points, points[1:]), lengths):
        if seen + seg >= target:
            local = (target - seen) / seg if seg else 0
            return a[0] + (b[0] - a[0]) * local, a[1] + (b[1] - a[1]) * local
        seen += seg
    return points[-1]


def draw_guard_board(draw: ImageDraw.ImageDraw, p: float) -> None:
    card(draw, (780, 218, 1192, 610), WHITE, LINE)
    draw.text((806, 242), "运行前检查与失败回滚", font=FONTS["h2"], fill=INK)
    labels = [
        ("订单 O100", "已支付，未发货", True, GREEN),
        ("订单 O200", "已发货，禁止取消", p > 0.44, RED),
    ]
    yy = 300
    for title, body, active, color in labels:
        capability_card(draw, (812, yy, 1158, yy + 80), title, body, color, active)
        yy += 92
    if p > 0.62:
        capability_card(draw, (812, 506, 1158, 574), "补偿动作", "审批拒绝或后续失败时，草稿按 actionId 反向撤销。", AMBER, True)


def draw_review_queue(draw: ImageDraw.ImageDraw, p: float) -> None:
    card(draw, (770, 218, 1190, 612), WHITE, LINE)
    draw.text((798, 242), "高风险动作进入审批", font=FONTS["h2"], fill=INK)
    capability_card(draw, (808, 300, 1152, 390), "Action 标记", "riskLevel = HIGH\nrequiresHumanReview = true", PURPLE, True)
    status = "等待复核" if p < 0.58 else "审批通过"
    color = AMBER if p < 0.58 else GREEN
    capability_card(draw, (808, 414, 1152, 522), "审批任务", f"动作：operations.approval.request\n状态：{status}\n处理人：ops-lead", color, True)
    if p > 0.72:
        capability_card(draw, (808, 544, 1152, 590), "恢复点", "审批通过后，从挂起位置继续运行。", GREEN, True)


def draw_config_matrix(draw: ImageDraw.ImageDraw, p: float) -> None:
    card(draw, (770, 210, 1190, 622), WHITE, LINE)
    draw.text((796, 235), "配置带来的效果", font=FONTS["h2"], fill=INK)
    rows = [
        ("auto-register", "Spring Bean 自动注册为 Action", GREEN),
        ("validation: FAIL", "启动时发现断链直接失败", RED),
        ("execution.policies", "重试、退避、超时可配置", BLUE),
        ("persistence.jdbc", "挂起运行和 Trace 可恢复", AMBER),
        ("human-review", "审批任务和回调入口打开", PURPLE),
        ("masking + limits", "脱敏、审批阈值、硬拒绝", CYAN),
        ("console.enabled", "只读看板与审计导出", GREEN),
    ]
    yy = 292
    for i, (key, value, color) in enumerate(rows):
        active = p > 0.08 + i * 0.1
        rounded(draw, (802, yy, 1160, yy + 38), 7, mix(WHITE, GREEN_SOFT if color == GREEN else BLUE_SOFT if color == BLUE else AMBER_SOFT if color == AMBER else PURPLE_SOFT if color == PURPLE else CYAN_SOFT if color == CYAN else RED_SOFT, 0.82 if active else 0.1), color if active else LINE)
        draw.text((818, yy + 10), key, font=CODE_FONTS["small"], fill=INK if active else MUTED)
        draw.text((970, yy + 10), value, font=FONTS["tiny"], fill=INK_2 if active else MUTED)
        yy += 44


def draw_run_dashboard(draw: ImageDraw.ImageDraw, p: float) -> None:
    card(draw, (762, 206, 1196, 628), WHITE, LINE)
    draw.text((790, 232), "运行效果看板", font=FONTS["h2"], fill=INK)
    draw.text((792, 272), "RUN-2026-0611-001", font=CODE_FONTS["small"], fill=GREEN_DARK)
    steps = [
        ("order.lookup", "订单已载入", BLUE),
        ("eligibility.check", "资格已确认", BLUE),
        ("request.draft", "草稿已创建", AMBER),
        ("approval.request", "等待审批", PURPLE),
    ]
    yy = 318
    active_idx = min(3, int(ease_out(p) * 4.2))
    for i, (action, result, color) in enumerate(steps):
        active = i <= active_idx
        rounded(draw, (794, yy, 1162, yy + 48), 8, mix(WHITE, GREEN_SOFT if color == GREEN else BLUE_SOFT if color == BLUE else AMBER_SOFT if color == AMBER else PURPLE_SOFT, 0.78 if active else 0.1), color if active else LINE)
        draw.text((812, yy + 8), action, font=CODE_FONTS["small"], fill=INK_2 if active else MUTED)
        draw.text((1030, yy + 8), result if active else "待执行", font=FONTS["tiny"], fill=INK_2 if active else MUTED)
        yy += 58
    if p > 0.74:
        capability_card(draw, (794, 552, 1162, 604), "当前状态", "SUSPENDED_FOR_HUMAN_REVIEW", AMBER, True)


def draw_resume_board(draw: ImageDraw.ImageDraw, p: float) -> None:
    card(draw, (760, 215, 1194, 618), WHITE, LINE)
    draw.text((790, 242), "审批后继续运行", font=FONTS["h2"], fill=INK)
    stages = [
        ("审批任务生成", "待处理", AMBER),
        ("ops-lead 复核", "已通过", GREEN),
        ("恢复运行", "继续执行", BLUE),
        ("目标完成", "COMPLETED", GREEN),
    ]
    yy = 302
    active_idx = min(3, int(ease_out(p) * 4.0))
    for i, (title, status, color) in enumerate(stages):
        active = i <= active_idx
        rounded(draw, (800, yy, 1154, yy + 56), 8, mix(WHITE, GREEN_SOFT if color == GREEN else BLUE_SOFT if color == BLUE else AMBER_SOFT, 0.8 if active else 0.12), color if active else LINE)
        draw.text((822, yy + 11), title, font=FONTS["small"], fill=INK if active else MUTED)
        draw.text((1030, yy + 11), status if active else "等待", font=FONTS["small"], fill=INK_2 if active else MUTED)
        yy += 68


def draw_audit_board(draw: ImageDraw.ImageDraw, p: float) -> None:
    card(draw, (762, 206, 1196, 630), WHITE, LINE)
    draw.text((790, 232), "控制台看到的证据", font=FONTS["h2"], fill=INK)
    events = [
        ("RUN_STARTED", "请求来源已记录", GREEN),
        ("ACTION_STARTED", "order.lookup", BLUE),
        ("ACTION_COMPLETED", "草稿创建", AMBER),
        ("HUMAN_REVIEW_REQUESTED", "等待复核", PURPLE),
        ("RUN_RESUMED", "审批通过", GREEN),
        ("TRACE_CHAIN_VERIFIED", "哈希链有效", CYAN),
    ]
    yy = 292
    active_count = int(ease_out(p) * (len(events) + 0.7))
    for i, (event, detail, color) in enumerate(events):
        active = i < active_count
        draw.line((820, yy + 18, 820, yy + 56), fill=LINE, width=3)
        draw.ellipse((812, yy + 10, 828, yy + 26), fill=color if active else FAINT)
        draw.text((846, yy + 4), event, font=CODE_FONTS["small"], fill=INK if active else MUTED)
        draw.text((846, yy + 24), detail, font=FONTS["tiny"], fill=INK_2 if active else MUTED)
        yy += 52
    if p > 0.78:
        rounded(draw, (820, 580, 1140, 610), 15, GREEN_SOFT, GREEN)
        draw.text((846, 588), "审计链完整：有效，可导出 JSONL", font=FONTS["tiny"], fill=GREEN_DARK)


def scene_intro(draw: ImageDraw.ImageDraw, p: float, t: float) -> None:
    section_title(draw, "00", "从普通业务代码到可治理运行", "这版视频只讲一件事：开发者怎么接入 ActionGraph，以及代码写完后系统立即获得什么能力。")
    card(draw, (70, 235, 390, 570), WHITE, LINE)
    draw.text((98, 265), "接入前", font=FONTS["h2"], fill=INK)
    draw_text(draw, (98, 322), "普通服务方法直接调用：能跑，但缺少规划、审批、恢复和可验证审计。", FONTS["body"], MUTED, max_width=245)
    arrow(draw, (420, 405), (500, 405), GREEN, 5)
    card(draw, (535, 220, 1190, 600), WHITE, LINE)
    draw.text((565, 250), "接入后", font=FONTS["h2"], fill=INK)
    items = [
        ("代码", "普通 Spring 方法加注解", GREEN),
        ("运行", "目标驱动，自动规划路径", BLUE),
        ("治理", "Guard、人审、补偿、阈值", AMBER),
        ("证据", "Trace、脱敏、哈希链、导出", PURPLE),
    ]
    for i, (title, body, color) in enumerate(items):
        a = ease((p - 0.12 - i * 0.13) / 0.22)
        if a <= 0:
            continue
        x = 585 + (i % 2) * 290
        y = 320 + (i // 2) * 105
        capability_card(draw, (x, y, x + 255, y + 78), title, body, color, True)


def scene_gradle(draw: ImageDraw.ImageDraw, p: float, t: float) -> None:
    section_title(draw, "01", "第一步：接入依赖", "不是引入一个大平台，而是把业务应用接上 BOM 和 Starter，让 Spring 能发现 Action。")
    draw_ide(draw, (54, 218, 742, 630), "build.gradle.kts", GRADLE_CODE, p / 0.72, active_lines=[8, 9], current_tree="build.gradle.kts")
    draw_module_stack(draw, p)


def scene_lookup(draw: ImageDraw.ImageDraw, p: float, t: float) -> None:
    section_title(draw, "02", "第二步：把一个方法声明成 Action", "id 是业务动作名，preconditions 是执行前需要满足的事实，effects 是执行后产生的新事实。")
    draw_ide(draw, (54, 214, 742, 642), "OrderCancellationWorkflow.java", WORKFLOW_IMPORTS_CODE, p / 0.76, active_lines=[3, 4, 5, 16, 17, 18, 19], current_tree="Workflow.java")
    draw_action_graph(draw, (770, 228, 1190, 610), 1 if p > 0.55 else 0, clamp((p - 0.45) / 0.35))


def scene_path(draw: ImageDraw.ImageDraw, p: float, t: float) -> None:
    section_title(draw, "03", "第三步：继续补 Action，路径自然长出来", "每个方法只描述自己的输入事实和输出事实，框架负责把它们串成可执行计划。")
    draw_ide(draw, (54, 212, 742, 644), "OrderCancellationWorkflow.java", WORKFLOW_PATH_CODE, p / 0.78, active_lines=[1, 3, 4, 10, 12, 16, 17, 18], current_tree="Workflow.java")
    active = 1 + min(2, int(ease_out(p) * 3.0))
    draw_action_graph(draw, (770, 228, 1190, 610), active, clamp(p * 0.75))


def scene_guard(draw: ImageDraw.ImageDraw, p: float, t: float) -> None:
    section_title(draw, "04", "第四步：把风险判断和补偿写在动作旁边", "Guard 决定当前事实下能不能执行；Compensation 负责在拒绝或失败时撤销已产生的副作用。")
    draw_ide(draw, (54, 218, 742, 630), "OrderCancellationWorkflow.java", GUARD_CODE, p / 0.72, active_lines=[1, 3, 6, 8], current_tree="Workflow.java")
    draw_guard_board(draw, p)


def scene_review(draw: ImageDraw.ImageDraw, p: float, t: float) -> None:
    section_title(draw, "05", "第五步：高风险动作显式进入人审", "开发者不用写流程引擎分支，只需要把风险语义写到 Action 契约里。")
    draw_ide(draw, (54, 218, 742, 630), "OrderCancellationWorkflow.java", REVIEW_CODE, p / 0.72, active_lines=[5, 6, 7], current_tree="Workflow.java")
    draw_review_queue(draw, p)


def scene_config(draw: ImageDraw.ImageDraw, p: float, t: float) -> None:
    section_title(draw, "06", "第六步：用配置打开运行能力", "这里不是堆配置，而是按能力域开启：注册、校验、持久化、人审、脱敏、限额和控制台。")
    draw_ide(draw, (54, 198, 742, 654), "application-actiongraph.yml", CONFIG_CODE, p / 0.82, active_lines=[2, 5, 11, 20, 25, 31, 36, 42], current_tree="application.yml")
    draw_config_matrix(draw, p)


def scene_run(draw: ImageDraw.ImageDraw, p: float, t: float) -> None:
    section_title(draw, "07", "第七步：业务入口只提交目标和初始事实", "运行效果不靠命令行解释：右侧直接展示一次订单取消请求在运行态如何推进。")
    draw_ide(draw, (54, 198, 742, 654), "CancellationRunService.java", RUN_CODE, p / 0.78, active_lines=[12, 13, 14, 16, 17, 18], current_tree="RunService.java")
    draw_run_dashboard(draw, p)


def scene_resume(draw: ImageDraw.ImageDraw, p: float, t: float) -> None:
    section_title(draw, "08", "第八步：审批通过后从挂起点恢复", "审批系统只做决策；ActionGraph 负责找到挂起运行、校验阶段、恢复事实并继续执行。")
    draw_ide(draw, (54, 218, 742, 630), "ReviewGateway.java", REVIEW_CLIENT_CODE, p / 0.74, active_lines=[4, 6, 10], current_tree="ReviewClient.java")
    draw_resume_board(draw, p)


def scene_audit(draw: ImageDraw.ImageDraw, p: float, t: float) -> None:
    section_title(draw, "09", "第九步：运行证据自动进入控制台", "技术人员看到的是 Trace、状态、脱敏数据和哈希链校验；业务侧拿到的是可追溯的审计证据。")
    draw_ide(draw, (54, 218, 742, 630), "AuditConsoleUsage.java", CONSOLE_CODE, p / 0.7, active_lines=[4, 5, 7], current_tree="ConsoleClient.java")
    draw_audit_board(draw, p)


def scene_recap(draw: ImageDraw.ImageDraw, p: float, t: float) -> None:
    section_title(draw, "10", "看完后应该记住什么", "ActionGraph 的接入点很小，但运行时获得的是一套完整的业务执行模型。")
    rows = [
        ("Action 契约", "方法变成可规划、可审批、可补偿的业务能力", GREEN),
        ("配置策略", "重试、超时、持久化、人审、脱敏、限额按域开启", BLUE),
        ("动态规划", "事实变化后，框架自动选择当前可达路径", AMBER),
        ("审批恢复", "挂起和恢复属于同一个运行状态机", PURPLE),
        ("审计证据", "Trace、哈希链和导出在运行过程中产生", CYAN),
    ]
    yy = 232
    for i, (title, body, color) in enumerate(rows):
        active = p > i * 0.13
        capability_card(draw, (168, yy, 1114, yy + 56), title, body, color, active)
        yy += 68


SCENE_RENDERERS = [
    scene_intro,
    scene_gradle,
    scene_lookup,
    scene_path,
    scene_guard,
    scene_review,
    scene_config,
    scene_run,
    scene_resume,
    scene_audit,
    scene_recap,
]


def render_frame(t: float) -> Image.Image:
    scene_idx, p, label = scene_bounds(t)
    img = background(t)
    draw = ImageDraw.Draw(img, "RGBA")
    header(draw, t, label)
    SCENE_RENDERERS[scene_idx](draw, p, t)
    return img


def write_video(output: Path, poster: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    poster.parent.mkdir(parents=True, exist_ok=True)
    total_frames = int(DURATION * FPS)
    poster_frame = render_frame(9.5)
    poster_frame.save(poster)
    with imageio.get_writer(
        output,
        fps=FPS,
        codec="libx264",
        quality=8,
        pixelformat="yuv420p",
        macro_block_size=1,
    ) as writer:
        for frame in range(total_frames):
            t = frame / FPS
            writer.append_data(np.asarray(render_frame(t)))
            if frame and frame % (FPS * 20) == 0:
                print(f"rendered {frame}/{total_frames} frames")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--poster", type=Path, default=DEFAULT_POSTER)
    args = parser.parse_args()
    write_video(args.output, args.poster)
    print(f"wrote {args.output}")
    print(f"wrote {args.poster}")


if __name__ == "__main__":
    main()
