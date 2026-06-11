#!/usr/bin/env python3
"""Render the ActionGraph tutorial video as dynamic MP4 frames.

This renderer deliberately avoids HTML/browser capture. Frames are drawn with
Pillow and streamed to ffmpeg through imageio.
"""

from __future__ import annotations

import argparse
import math
from pathlib import Path
from typing import Iterable, Sequence

try:
    import imageio.v2 as imageio
except Exception as exc:  # pragma: no cover - user-facing dependency check
    raise SystemExit(
        "Missing imageio/imageio-ffmpeg. Install them into a temporary path, for example:\n"
        "python3 -m pip install --target /tmp/codex-video-deps imageio imageio-ffmpeg\n"
        "PYTHONPATH=/tmp/codex-video-deps python3 tools/render_actiongraph_tutorial_video.py"
    ) from exc

import numpy as np
from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT = ROOT / "docs/assets/actiongraph-executive-demo.mp4"
DEFAULT_POSTER = ROOT / "docs/assets/actiongraph-executive-demo-poster.png"

W = 1280
H = 720
FPS = 24
DURATION = 76.0

INK = (18, 33, 27)
INK_2 = (36, 52, 45)
MUTED = (95, 111, 104)
FAINT = (132, 145, 139)
LINE = (219, 228, 223)
GREEN = (32, 160, 91)
GREEN_DARK = (15, 125, 67)
GREEN_SOFT = (229, 246, 237)
BLUE = (36, 103, 214)
BLUE_SOFT = (232, 240, 255)
AMBER = (201, 123, 19)
AMBER_SOFT = (255, 243, 223)
CORAL = (201, 78, 78)
CORAL_SOFT = (255, 240, 238)
CODE_BG = (16, 25, 20)
WHITE = (255, 255, 255)

SCENES = [
    (0.0, 8.0, "开场"),
    (8.0, 18.0, "一句话发起"),
    (18.0, 28.0, "安全路线"),
    (28.0, 42.0, "自动改道"),
    (42.0, 55.0, "审批链"),
    (55.0, 66.0, "审计证据"),
    (66.0, 76.0, "推广价值"),
]


def clamp(v: float, lo: float = 0.0, hi: float = 1.0) -> float:
    return max(lo, min(hi, v))


def ease(v: float) -> float:
    v = clamp(v)
    return v * v * (3 - 2 * v)


def ease_out(v: float) -> float:
    v = clamp(v)
    return 1 - (1 - v) ** 3


def lerp(a: float, b: float, p: float) -> float:
    return a + (b - a) * p


def mix(c1: tuple[int, int, int], c2: tuple[int, int, int], p: float) -> tuple[int, int, int]:
    p = clamp(p)
    return tuple(int(lerp(a, b, p)) for a, b in zip(c1, c2))


def with_alpha(color: tuple[int, int, int], alpha: int) -> tuple[int, int, int, int]:
    return color[0], color[1], color[2], alpha


def font_path() -> str:
    candidates = [
        "/System/Library/Fonts/STHeiti Medium.ttc",
        "/System/Library/Fonts/PingFang.ttc",
        "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
        "/Library/Fonts/Arial Unicode.ttf",
    ]
    for candidate in candidates:
        if Path(candidate).exists():
            return candidate
    return ""


FONT_PATH = font_path()


def font(size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    if FONT_PATH:
        return ImageFont.truetype(FONT_PATH, size=size)
    return ImageFont.load_default()


FONTS = {
    "hero": font(58),
    "h1": font(42),
    "h2": font(30),
    "h3": font(24),
    "body": font(20),
    "small": font(16),
    "tiny": font(13),
    "mono": font(16),
}


def text_bbox(draw: ImageDraw.ImageDraw, text: str, fnt: ImageFont.ImageFont) -> tuple[int, int]:
    if not text:
        return 0, 0
    box = draw.textbbox((0, 0), text, font=fnt)
    return box[2] - box[0], box[3] - box[1]


def wrap(draw: ImageDraw.ImageDraw, text: str, fnt: ImageFont.ImageFont, max_width: int) -> list[str]:
    lines: list[str] = []
    for para in text.split("\n"):
        line = ""
        for ch in para:
            trial = line + ch
            if text_bbox(draw, trial, fnt)[0] <= max_width or not line:
                line = trial
            else:
                lines.append(line)
                line = ch
        if line:
            lines.append(line)
    return lines


def draw_text(
    draw: ImageDraw.ImageDraw,
    xy: tuple[float, float],
    text: str,
    fnt: ImageFont.ImageFont,
    fill: tuple[int, int, int] = INK,
    max_width: int | None = None,
    line_gap: int = 8,
    alpha: int = 255,
) -> int:
    x, y = xy
    lines = [text] if max_width is None else wrap(draw, text, fnt, max_width)
    for line in lines:
        draw.text((int(x), int(y)), line, font=fnt, fill=with_alpha(fill, alpha))
        _, h = text_bbox(draw, line, fnt)
        y += h + line_gap
    return int(y)


def rounded(
    draw: ImageDraw.ImageDraw,
    box: tuple[float, float, float, float],
    radius: int,
    fill: tuple[int, int, int] | tuple[int, int, int, int],
    outline: tuple[int, int, int] | tuple[int, int, int, int] | None = None,
    width: int = 1,
) -> None:
    draw.rounded_rectangle(tuple(int(v) for v in box), radius=radius, fill=fill, outline=outline, width=width)


def card(
    draw: ImageDraw.ImageDraw,
    box: tuple[float, float, float, float],
    fill: tuple[int, int, int] = WHITE,
    outline: tuple[int, int, int] = LINE,
    radius: int = 8,
) -> None:
    rounded(draw, box, radius, fill, outline, 1)


def arrow(
    draw: ImageDraw.ImageDraw,
    start: tuple[float, float],
    end: tuple[float, float],
    color: tuple[int, int, int] = GREEN,
    width: int = 4,
    alpha: int = 255,
) -> None:
    sx, sy = start
    ex, ey = end
    draw.line((sx, sy, ex, ey), fill=with_alpha(color, alpha), width=width)
    ang = math.atan2(ey - sy, ex - sx)
    size = 11 + width
    p1 = (ex, ey)
    p2 = (ex - size * math.cos(ang - 0.45), ey - size * math.sin(ang - 0.45))
    p3 = (ex - size * math.cos(ang + 0.45), ey - size * math.sin(ang + 0.45))
    draw.polygon((p1, p2, p3), fill=with_alpha(color, alpha))


def line_path(draw: ImageDraw.ImageDraw, points: Sequence[tuple[float, float]], color=LINE, width=3, alpha=255) -> None:
    for a, b in zip(points, points[1:]):
        draw.line((*a, *b), fill=with_alpha(color, alpha), width=width)


def point_on_path(points: Sequence[tuple[float, float]], p: float) -> tuple[float, float]:
    p = clamp(p)
    lengths = []
    total = 0.0
    for a, b in zip(points, points[1:]):
        seg = math.hypot(b[0] - a[0], b[1] - a[1])
        lengths.append(seg)
        total += seg
    target = total * p
    acc = 0.0
    for (a, b), seg in zip(zip(points, points[1:]), lengths):
        if acc + seg >= target:
            local = (target - acc) / seg if seg else 0
            return lerp(a[0], b[0], local), lerp(a[1], b[1], local)
        acc += seg
    return points[-1]


def draw_node(
    draw: ImageDraw.ImageDraw,
    xy: tuple[float, float],
    label: str,
    active: bool = False,
    color: tuple[int, int, int] = GREEN,
    scale: float = 1.0,
) -> None:
    x, y = xy
    r = 34 * scale
    fill = mix(WHITE, GREEN_SOFT if color == GREEN else BLUE_SOFT, 0.65 if active else 0.15)
    outline = color if active else LINE
    rounded(draw, (x - r, y - r, x + r, y + r), 8, fill, outline, 2 if active else 1)
    draw.ellipse((x - 7 * scale, y - 7 * scale, x + 7 * scale, y + 7 * scale), fill=color if active else FAINT)
    tw, _ = text_bbox(draw, label, FONTS["tiny"])
    draw.text((int(x - tw / 2), int(y + r + 8)), label, font=FONTS["tiny"], fill=INK_2)


def draw_logo_mark(draw: ImageDraw.ImageDraw, x: int, y: int, size: int = 48) -> None:
    rounded(draw, (x, y, x + size, y + size), 9, (17, 37, 27), None)
    cx, cy = x + size / 2, y + size / 2
    hexagon = []
    for i in range(6):
        ang = math.pi / 6 + i * math.pi / 3
        hexagon.append((cx + math.cos(ang) * size * 0.34, cy + math.sin(ang) * size * 0.34))
    draw.polygon(hexagon, fill=(22, 51, 36), outline=(47, 111, 73))
    pts = [
        (x + size * 0.24, y + size * 0.68),
        (x + size * 0.42, y + size * 0.50),
        (x + size * 0.57, y + size * 0.58),
        (x + size * 0.76, y + size * 0.30),
    ]
    line_path(draw, pts, BLUE, 3, 255)
    diamond = [
        (cx, cy - size * 0.19),
        (cx + size * 0.19, cy),
        (cx, cy + size * 0.19),
        (cx - size * 0.19, cy),
    ]
    draw.polygon(diamond, fill=(20, 60, 39), outline=(157, 240, 178))
    draw.line((cx - 6, cy, cx - 1, cy + 5, cx + 9, cy - 7), fill=(255, 208, 122), width=3)
    for px, py in pts:
        draw.ellipse((px - 4, py - 4, px + 4, py + 4), fill=(157, 240, 178), outline=(17, 37, 27), width=2)


def create_background(w: int, h: int) -> Image.Image:
    img = Image.new("RGBA", (w, h), WHITE)
    draw = ImageDraw.Draw(img, "RGBA")
    for y in range(h):
        p = y / h
        color = mix((250, 253, 251), (236, 244, 248), p)
        draw.line((0, y, w, y), fill=color)
    for x in range(0, w, 64):
        draw.line((x, 0, x, h), fill=(220, 229, 225, 75), width=1)
    for y in range(0, h, 64):
        draw.line((0, y, w, y), fill=(220, 229, 225, 70), width=1)
    return img


BACKGROUND = create_background(W, H)


def scene_bounds(t: float) -> tuple[int, float]:
    for i, (start, end, _) in enumerate(SCENES):
        if start <= t < end:
            return i, (t - start) / (end - start)
    return len(SCENES) - 1, 1.0


def title_bar(draw: ImageDraw.ImageDraw, t: float, section: str) -> None:
    draw_logo_mark(draw, 44, 34, 46)
    draw.text((102, 38), "ActionGraph", font=FONTS["h3"], fill=INK)
    draw.text((103, 66), "Governed Agent Runtime", font=FONTS["tiny"], fill=MUTED)
    rounded(draw, (995, 38, 1195, 68), 15, (255, 255, 255, 190), LINE)
    draw.text((1017, 46), section, font=FONTS["tiny"], fill=GREEN_DARK)
    progress = clamp(t / DURATION)
    draw.rounded_rectangle((44, 690, 1236, 698), radius=4, fill=(224, 233, 228))
    draw.rounded_rectangle((44, 690, 44 + int(1192 * progress), 698), radius=4, fill=GREEN)


def draw_background_motion(draw: ImageDraw.ImageDraw, t: float) -> None:
    for i in range(22):
        x = (90 + i * 71 + math.sin(t * 0.45 + i) * 16) % W
        y = 120 + ((i * 47 + t * 18) % 510)
        a = int(34 + 30 * (0.5 + 0.5 * math.sin(t + i)))
        draw.ellipse((x - 2, y - 2, x + 2, y + 2), fill=(36, 103, 214, a))


def scene_opening(img: Image.Image, draw: ImageDraw.ImageDraw, p: float, t: float) -> None:
    q = ease_out(p)
    draw_text(draw, (86, 150 - 30 * (1 - q)), "让 AI 不只会回答", FONTS["h1"], INK, alpha=int(255 * q))
    draw_text(draw, (86, 205 - 30 * (1 - q)), "还能安全办事", FONTS["hero"], INK, alpha=int(255 * q))
    draw_text(
        draw,
        (90, 300),
        "一句话发起流程，系统自动走规则、过审批、留证据，越线前主动停下。",
        FONTS["body"],
        MUTED,
        max_width=560,
        alpha=int(255 * ease((p - 0.12) / 0.4)),
    )
    pts = [(675, 360), (790, 275), (910, 365), (1030, 245), (1150, 330)]
    line_path(draw, pts, LINE, 4, 180)
    path_p = ease((p - 0.18) / 0.68)
    for a, b in zip(pts, pts[1:]):
        segp = clamp((path_p * (len(pts) - 1)) - list(zip(pts, pts[1:])).index((a, b)))
        if segp > 0:
            end = (lerp(a[0], b[0], segp), lerp(a[1], b[1], segp))
            arrow(draw, a, end, GREEN, 5, 230)
    labels = ["请求", "规则", "执行", "审批", "审计"]
    for i, pt in enumerate(pts):
        active = path_p > i / (len(pts) - 1) - 0.02
        draw_node(draw, pt, labels[i], active=active, color=GREEN if i != 3 else AMBER, scale=1.05)
    if p > 0.35:
        x, y = point_on_path(pts, min(1, (p - 0.35) / 0.5))
        draw.ellipse((x - 12, y - 12, x + 12, y + 12), fill=with_alpha(GREEN, 220))


def scene_intent(img: Image.Image, draw: ImageDraw.ImageDraw, p: float, t: float) -> None:
    draw_text(draw, (78, 122), "01 一句话发起业务流程", FONTS["h1"], INK)
    bubble_x = lerp(-420, 78, ease_out(p / 0.22))
    card(draw, (bubble_x, 225, bubble_x + 400, 340), WHITE, LINE)
    draw_text(draw, (bubble_x + 24, 248), "为客户 C001 预约 80 万元的大额转账", FONTS["body"], INK_2, max_width=340)
    card(draw, (520, 210, 780, 358), (250, 253, 251), GREEN)
    draw_text(draw, (548, 238), "意图识别", FONTS["h2"], INK)
    draw_text(draw, (552, 284), "识别业务目标\n缺信息就追问", FONTS["small"], MUTED, max_width=200)
    if p > 0.2:
        arrow(draw, (bubble_x + 400, 282), (520, 282), BLUE, 4, int(255 * ease((p - 0.2) / 0.22)))
    chips = [
        ("任务：大额转账预约", 865, 215, GREEN),
        ("客户：C001", 890, 285, BLUE),
        ("金额：80 万", 910, 355, AMBER),
    ]
    for i, (label, x, y, color) in enumerate(chips):
        a = ease((p - 0.42 - i * 0.08) / 0.24)
        if a <= 0:
            continue
        rounded(draw, (x, y - 18 + 16 * (1 - a), x + 255, y + 34 + 16 * (1 - a)), 8, mix(WHITE, GREEN_SOFT if color == GREEN else BLUE_SOFT if color == BLUE else AMBER_SOFT, 0.8), color, 1)
        draw.text((x + 18, y - 4 + 16 * (1 - a)), label, font=FONTS["small"], fill=with_alpha(INK_2, int(255 * a)))
    if p > 0.74:
        draw_text(draw, (180, 455), "听得懂业务语言，但不会越权替人做决定", FONTS["h2"], GREEN_DARK, max_width=760)
        draw_text(draw, (185, 502), "信息齐了才推进；缺信息就追问；后续执行必须经过企业规则。", FONTS["body"], MUTED, max_width=850)


def scene_contract(img: Image.Image, draw: ImageDraw.ImageDraw, p: float, t: float) -> None:
    draw_text(draw, (78, 122), "02 只走企业授权过的路", FONTS["h1"], INK)
    nodes = [(255, 380), (455, 260), (655, 380), (855, 260), (1055, 380)]
    labels = ["客户核验", "余额检查", "风险评估", "生成草稿", "人工审批"]
    line_path(draw, nodes, LINE, 4, 180)
    prog = ease((p - 0.1) / 0.55)
    for i, (a, b) in enumerate(zip(nodes, nodes[1:])):
        local = clamp(prog * 4 - i)
        if local > 0:
            end = (lerp(a[0], b[0], local), lerp(a[1], b[1], local))
            arrow(draw, a, end, BLUE if i < 2 else GREEN, 4, 230)
    blocked = p < 0.48
    for i, node in enumerate(nodes):
        active = prog > i / 4 - 0.03
        color = CORAL if blocked and i == 2 and p > 0.18 else GREEN
        jitter = math.sin(t * 16) * 7 if color == CORAL else 0
        label = "未授权捷径" if color == CORAL else labels[i]
        draw_node(draw, (node[0] + jitter, node[1]), label, active=active, color=color)
    if p > 0.5:
        rounded(draw, (380, 500, 900, 575), 8, GREEN_SOFT, GREEN, 2)
        draw_text(draw, (410, 520), "已按企业制度选择合规路径", FONTS["h2"], GREEN_DARK)
    else:
        rounded(draw, (380, 500, 900, 575), 8, CORAL_SOFT, CORAL, 2)
        draw_text(draw, (410, 520), "未授权路径被拦截，不能绕过规则", FONTS["h2"], CORAL)


def scene_execution(img: Image.Image, draw: ImageDraw.ImageDraw, p: float, t: float) -> None:
    draw_text(draw, (78, 108), "03 系统异常时，自动走备用方案", FONTS["h1"], INK)
    top = [(140, 360), (350, 360), (560, 360), (770, 360), (980, 360), (1160, 360)]
    labels = ["核身", "实时\n余额", "风险", "草稿", "审批", "完成"]
    line_path(draw, top, LINE, 4, 200)
    for i, node in enumerate(top):
        draw_node(draw, node, labels[i], active=(i / 5) < p, color=BLUE if i < 2 else GREEN)
    # Retry pulses around realtime balance.
    if 0.22 < p < 0.44:
        for k in range(2):
            phase = (p - 0.22) * 8 - k
            if 0 <= phase <= 1:
                r = 38 + 28 * phase
                draw.ellipse((350 - r, 360 - r, 350 + r, 360 + r), outline=with_alpha(CORAL, int(180 * (1 - phase))), width=3)
        draw_text(draw, (278, 455), "实时接口抖动 → 自动重试", FONTS["small"], CORAL)
    reroute = p > 0.42
    if reroute:
        alt = [(350, 360), (455, 500), (645, 500), (770, 360)]
        line_path(draw, alt, (190, 202, 196), 3, 220)
        prog = ease((p - 0.42) / 0.34)
        for a, b in zip(alt, alt[1:]):
            idx = list(zip(alt, alt[1:])).index((a, b))
            local = clamp(prog * 3 - idx)
            if local > 0:
                arrow(draw, a, (lerp(a[0], b[0], local), lerp(a[1], b[1], local)), AMBER, 4, 230)
        draw_node(draw, (550, 500), "备用\n快照余额", active=True, color=AMBER)
        draw_text(draw, (408, 575), "不靠人工救火，按批准过的备用路线继续推进", FONTS["body"], MUTED)
    token_path = top if p < 0.42 else [(140, 360), (350, 360), (455, 500), (645, 500), (770, 360), (980, 360), (1160, 360)]
    x, y = point_on_path(token_path, ease(p))
    draw.ellipse((x - 13, y - 13, x + 13, y + 13), fill=with_alpha(GREEN, 235), outline=WHITE, width=3)


def scene_review(img: Image.Image, draw: ImageDraw.ImageDraw, p: float, t: float) -> None:
    draw_text(draw, (78, 108), "04 大额业务自动进入审批链", FONTS["h1"], INK)
    gauge_x, gauge_y = 110, 250
    card(draw, (gauge_x, gauge_y, gauge_x + 350, gauge_y + 280), WHITE, LINE)
    draw_text(draw, (gauge_x + 24, gauge_y + 28), "金额风控", FONTS["h2"], INK)
    draw.line((gauge_x + 44, gauge_y + 155, gauge_x + 306, gauge_y + 155), fill=LINE, width=10)
    draw.line((gauge_x + 44, gauge_y + 155, gauge_x + 44 + int(262 * min(0.82, ease(p) * 0.9)), gauge_y + 155), fill=AMBER, width=10)
    for x, label, color in [(gauge_x + 170, "review-limit", AMBER), (gauge_x + 295, "hard-limit", CORAL)]:
        draw.line((x, gauge_y + 132, x, gauge_y + 178), fill=color, width=3)
        cn = "审批线" if label == "review-limit" else "硬限额"
        draw.text((x - 26, gauge_y + 190), cn, font=FONTS["tiny"], fill=color)
    draw_text(draw, (gauge_x + 42, gauge_y + 86), "800,000 CNY", FONTS["h1"], AMBER)
    if p > 0.22:
        rounded(draw, (540, 235, 1150, 300), 8, AMBER_SOFT, AMBER, 2)
        draw_text(draw, (570, 254), "等待审批：流程暂停，证据保留", FONTS["h2"], AMBER)
    approvals = [("经办复核", 560), ("授权审批", 750), ("金额加签", 940)]
    for i, (label, x) in enumerate(approvals):
        a = ease((p - 0.32 - i * 0.1) / 0.22)
        if a <= 0:
            continue
        y = 372 - 26 * (1 - a)
        card(draw, (x, y, x + 170, y + 105), WHITE, GREEN if a > 0.9 else LINE)
        draw_text(draw, (x + 18, y + 20), label, FONTS["small"], INK_2, max_width=135, alpha=int(255 * a))
        if a > 0.8:
            draw.line((x + 22, y + 72, x + 42, y + 90, x + 78, y + 48), fill=GREEN, width=5)
    if p > 0.72:
        rounded(draw, (610, 555, 1085, 615), 8, GREEN_SOFT, GREEN, 2)
        draw_text(draw, (640, 572), "谁审批、为什么、到哪一步，一屏可查；批准后继续同一个业务案例", FONTS["body"], GREEN_DARK)


def scene_evidence(img: Image.Image, draw: ImageDraw.ImageDraw, p: float, t: float) -> None:
    draw_text(draw, (78, 108), "05 全程留痕，审计随时能拿", FONTS["h1"], INK)
    card(draw, (80, 190, 520, 590), WHITE, LINE)
    draw_text(draw, (110, 218), "业务证据时间线", FONTS["h2"], INK)
    events = ["收到请求", "完成核身", "余额重试", "发起审批", "审批通过", "流程完成"]
    visible = int(lerp(1, len(events), ease(p)))
    for i, event in enumerate(events[:visible]):
        y = 275 + i * 46
        color = AMBER if "REVIEW" in event or "RETRIED" in event else GREEN
        draw.ellipse((110, y, 126, y + 16), fill=color)
        draw.text((145, y - 2), event, font=FONTS["small"], fill=INK_2)
        if i > 0:
            draw.line((118, y - 30, 118, y), fill=LINE, width=2)
    blocks = [(620 + i * 130, 290 + (i % 2) * 70) for i in range(4)]
    for i, (x, y) in enumerate(blocks):
        a = ease((p - 0.18 - i * 0.08) / 0.22)
        if a <= 0:
            continue
        card(draw, (x, y, x + 105, y + 78), (250, 253, 251), GREEN if i < 3 else BLUE)
        draw.text((x + 18, y + 18), f"证据 {i+1}", font=FONTS["small"], fill=INK)
        draw.text((x + 18, y + 44), "已链上校验", font=FONTS["tiny"], fill=MUTED)
        if i > 0:
            prev = blocks[i - 1]
            arrow(draw, (prev[0] + 105, prev[1] + 39), (x, y + 39), BLUE, 3, 190)
    if p > 0.58:
        card(draw, (640, 500, 1140, 585), CORAL_SOFT, CORAL)
        draw_text(draw, (670, 520), "审批与审计展示自动脱敏：证件号、手机号不裸露", FONTS["body"], INK_2, max_width=430)


def scene_policy(img: Image.Image, draw: ImageDraw.ImageDraw, p: float, t: float) -> None:
    draw_text(draw, (78, 108), "06 越线前止损，业务动作前拦截", FONTS["h1"], INK)
    path = [(120, 365), (345, 365), (575, 365), (820, 365), (1070, 365)]
    labels = ["请求", "规则闸门", "草稿", "审批", "归档"]
    line_path(draw, path, LINE, 4, 180)
    for i, node in enumerate(path):
        active = i < 2 or (p > 0.78 and i == 4)
        color = CORAL if i == 1 else GREEN
        draw_node(draw, node, labels[i], active=active, color=color)
    token_p = min(0.32, ease(p) * 0.45)
    x, y = point_on_path(path, token_p)
    draw.ellipse((x - 13, y - 13, x + 13, y + 13), fill=with_alpha(CORAL, 235), outline=WHITE, width=3)
    if p > 0.28:
        rounded(draw, (235, 480, 660, 555), 8, CORAL_SOFT, CORAL, 2)
        draw_text(draw, (264, 499), "已拦截：600 万超过硬限额，草稿不会被创建", FONTS["body"], CORAL, max_width=360)
    if p > 0.64:
        cards = [("提效", 715, 485), ("风控", 715, 545), ("审计", 945, 485), ("老系统可接", 945, 545)]
        for label, x0, y0 in cards:
            card(draw, (x0, y0, x0 + 195, y0 + 48), WHITE, GREEN)
            draw.text((x0 + 16, y0 + 13), label, font=FONTS["small"], fill=INK_2)
        draw_text(draw, (700, 195), "从 AI 助手，到可推广的业务执行平台。", FONTS["h2"], GREEN_DARK, max_width=460)


SCENE_DRAWERS = [
    scene_opening,
    scene_intent,
    scene_contract,
    scene_execution,
    scene_review,
    scene_evidence,
    scene_policy,
]


def render_frame(t: float) -> Image.Image:
    img = BACKGROUND.copy()
    draw = ImageDraw.Draw(img, "RGBA")
    draw_background_motion(draw, t)
    scene_idx, local = scene_bounds(t)
    title_bar(draw, t, SCENES[scene_idx][2])
    SCENE_DRAWERS[scene_idx](img, draw, local, t)
    # Soft transition fade at scene boundaries.
    start, end, _ = SCENES[scene_idx]
    edge = min(t - start, end - t)
    if edge < 0.45 and 0 < scene_idx < len(SCENES):
        alpha = int(110 * (1 - clamp(edge / 0.45)))
        overlay = Image.new("RGBA", img.size, (255, 255, 255, alpha))
        img = Image.alpha_composite(img, overlay)
    return img.convert("RGB")


def render(output: Path, poster: Path, fps: int, seconds: float) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    poster.parent.mkdir(parents=True, exist_ok=True)
    total = int(seconds * fps)
    poster_frame = render_frame(2.0)
    poster_frame.save(poster)
    writer = imageio.get_writer(
        output,
        fps=fps,
        codec="libx264",
        quality=8,
        macro_block_size=16,
        ffmpeg_params=["-pix_fmt", "yuv420p", "-movflags", "+faststart"],
    )
    try:
        for i in range(total):
            t = i / fps
            frame = render_frame(t)
            writer.append_data(np.asarray(frame))
            if i % max(1, fps * 5) == 0:
                print(f"rendered {i}/{total} frames")
    finally:
        writer.close()
    print(f"video: {output}")
    print(f"poster: {poster}")


def main(argv: Iterable[str] | None = None) -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--poster", type=Path, default=DEFAULT_POSTER)
    parser.add_argument("--fps", type=int, default=FPS)
    parser.add_argument("--seconds", type=float, default=DURATION)
    args = parser.parse_args(list(argv) if argv is not None else None)
    render(args.output, args.poster, args.fps, args.seconds)


if __name__ == "__main__":
    main()
