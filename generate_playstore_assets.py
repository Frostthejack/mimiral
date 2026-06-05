#!/usr/bin/env python3
"""Generate Play Store icon and feature graphic for Mimiral."""

from PIL import Image, ImageDraw, ImageFont, ImageFilter
import math
import os

WORKTREE = r"C:\Users\luned\Documents\Projects\mimiral\.worktrees\t_69c1c0d3"

def create_playstore_icon(size=512):
    """Create a proper Play Store icon for Mimiral.
    
    Design: A stylized open book with a reading glass/magnifier,
    using a warm gradient background (deep blue to purple).
    """
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    # Background gradient (deep blue to purple)
    for y in range(size):
        ratio = y / size
        r = int(40 + ratio * 80)
        g = int(60 + ratio * 40)
        b = int(180 - ratio * 60)
        draw.line([(0, y), (size, y)], fill=(r, g, b, 255))
    
    # Rounded rectangle background
    margin = 20
    corner_radius = 80
    draw.rounded_rectangle(
        [margin, margin, size - margin, size - margin],
        radius=corner_radius,
        fill=(30, 40, 120, 230)
    )
    
    # Draw an open book shape
    cx, cy = size // 2, size // 2
    book_w = int(size * 0.55)
    book_h = int(size * 0.4)
    
    # Left page (white, slightly angled)
    left_page = [
        (cx - book_w//2, cy - book_h//2),
        (cx - 10, cy - book_h//2 - 15),
        (cx - 10, cy + book_h//2 - 10),
        (cx - book_w//2, cy + book_h//2),
    ]
    draw.polygon(left_page, fill=(245, 245, 255, 255))
    
    # Right page (white, slightly angled)
    right_page = [
        (cx + 10, cy - book_h//2 - 15),
        (cx + book_w//2, cy - book_h//2),
        (cx + book_w//2, cy + book_h//2),
        (cx + 10, cy + book_h//2 - 10),
    ]
    draw.polygon(right_page, fill=(255, 255, 255, 255))
    
    # Spine
    spine_w = 20
    draw.rectangle(
        [cx - spine_w//2, cy - book_h//2 - 15, cx + spine_w//2, cy + book_h//2],
        fill=(200, 200, 220, 255)
    )
    
    # Text lines on left page
    line_y_start = cy - book_h//2 + 30
    line_h = 18
    for i in range(5):
        y = line_y_start + i * line_h
        line_w = book_w//2 - 50
        draw.rectangle(
            [cx - book_w//2 + 25, y, cx - book_w//2 + 25 + line_w, y + 8],
            fill=(180, 180, 200, 180)
        )
    
    # Text lines on right page
    for i in range(5):
        y = line_y_start + i * line_h
        line_w = book_w//2 - 50
        draw.rectangle(
            [cx + 25, y, cx + 25 + line_w, y + 8],
            fill=(180, 180, 200, 180)
        )
    
    # Magnifying glass overlay
    mg_cx = cx + book_w//4
    mg_cy = cy - book_h//4
    mg_r = int(size * 0.12)
    
    # Glass circle
    draw.ellipse(
        [mg_cx - mg_r, mg_cy - mg_r, mg_cx + mg_r, mg_cy + mg_r],
        outline=(255, 200, 50, 255),
        width=8
    )
    # Glass tint
    overlay = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    overlay_draw = ImageDraw.Draw(overlay)
    overlay_draw.ellipse(
        [mg_cx - mg_r + 4, mg_cy - mg_r + 4, mg_cx + mg_r - 4, mg_cy + mg_r - 4],
        fill=(255, 220, 100, 40)
    )
    img = Image.alpha_composite(img, overlay)
    draw = ImageDraw.Draw(img)
    
    # Handle
    handle_len = int(size * 0.1)
    angle = math.radians(225)
    hx1 = mg_cx + int((mg_r - 5) * math.cos(angle))
    hy1 = mg_cy + int((mg_r - 5) * math.sin(angle))
    hx2 = hx1 + int(handle_len * math.cos(angle))
    hy2 = hy1 + int(handle_len * math.sin(angle))
    draw.line([(hx1, hy1), (hx2, hy2)], fill=(255, 200, 50, 255), width=10)
    
    # Small sparkle effects
    sparkles = [
        (cx - book_w//3, cy - book_h//2 - 40),
        (cx + book_w//3 + 20, cy - book_h//2 - 30),
        (cx + book_w//2 + 30, cy + book_h//3),
    ]
    for sx, sy in sparkles:
        draw.text((sx, sy), "★", fill=(255, 220, 100, 200))
    
    return img


def create_feature_graphic(width=1024, height=500):
    """Create a feature graphic for the Play Store listing."""
    img = Image.new('RGBA', (width, height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    # Background gradient
    for y in range(height):
        ratio = y / height
        r = int(30 + ratio * 50)
        g = int(50 + ratio * 30)
        b = int(160 - ratio * 40)
        draw.line([(0, y), (width, y)], fill=(r, g, b, 255))
    
    # Decorative circles
    for pos, r, alpha in [((100, 100), 200, 30), ((900, 400), 150, 25), ((500, 50), 100, 20)]:
        overlay = Image.new('RGBA', (width, height), (0, 0, 0, 0))
        overlay_draw = ImageDraw.Draw(overlay)
        overlay_draw.ellipse([pos[0]-r, pos[1]-r, pos[0]+r, pos[1]+r], fill=(255, 255, 255, alpha))
        img = Image.alpha_composite(img, overlay)
        draw = ImageDraw.Draw(img)
    
    # App name
    try:
        font_large = ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", 72)
        font_medium = ImageFont.truetype("C:/Windows/Fonts/arial.ttf", 36)
    except:
        font_large = ImageFont.load_default()
        font_medium = font_large
    
    # Title
    title = "Mimiral"
    bbox = draw.textbbox((0, 0), title, font=font_large)
    tw = bbox[2] - bbox[0]
    draw.text(((width - tw) // 2, 120), title, fill=(255, 255, 255, 255), font=font_large)
    
    # Tagline
    tagline = "Your Personal E-Book Library"
    bbox2 = draw.textbbox((0, 0), tagline, font=font_medium)
    tw2 = bbox2[2] - bbox2[0]
    draw.text(((width - tw2) // 2, 220), tagline, fill=(220, 220, 255, 255), font=font_medium)
    
    # Feature badges
    features = ["EPUB • PDF • MOBI • CBZ", "TTS • Highlights • Notes", "Cloud Sync • OPDS"]
    badge_y = 310
    try:
        font_small = ImageFont.truetype("C:/Windows/Fonts/arial.ttf", 24)
    except:
        font_small = font_medium
    
    for feat in features:
        bbox3 = draw.textbbox((0, 0), feat, font=font_small)
        tw3 = bbox3[2] - bbox3[0]
        # Badge background
        pad = 20
        draw.rounded_rectangle(
            [(width - tw3)//2 - pad, badge_y - 8, (width + tw3)//2 + pad, badge_y + 35],
            radius=20,
            fill=(255, 255, 255, 40)
        )
        draw.text(((width - tw3) // 2, badge_y), feat, fill=(255, 255, 255, 230), font=font_small)
        badge_y += 50
    
    return img


def create_phone_screenshot_mock(width=1080, height=1920):
    """Create a mock phone screenshot frame for the library screen."""
    img = Image.new('RGBA', (width, height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    # Phone frame
    frame_color = (40, 40, 50, 255)
    screen_color = (25, 25, 35, 255)
    
    # Rounded rectangle frame
    corner = 40
    draw.rounded_rectangle([0, 0, width, height], radius=corner, fill=frame_color)
    
    # Screen area
    screen_margin = 15
    draw.rounded_rectangle(
        [screen_margin, screen_margin, width - screen_margin, height - screen_margin],
        radius=corner - 5,
        fill=screen_color
    )
    
    # Status bar
    draw.rectangle([screen_margin, screen_margin, width - screen_margin, screen_margin + 60],
                    fill=(35, 35, 45, 255))
    
    try:
        font_title = ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", 32)
        font_body = ImageFont.truetype("C:/Windows/Fonts/arial.ttf", 24)
        font_small = ImageFont.truetype("C:/Windows/Fonts/arial.ttf", 18)
    except:
        font_title = font_body = font_small = ImageFont.load_default()
    
    # Status bar text
    draw.text((screen_margin + 20, screen_margin + 15), "Mimiral", fill=(200, 200, 220, 255), font=font_body)
    draw.text((width - screen_margin - 80, screen_margin + 15), "100% 🔋", fill=(200, 200, 220, 255), font=font_small)
    
    # App bar
    appbar_y = screen_margin + 60
    draw.rectangle([screen_margin, appbar_y, width - screen_margin, appbar_y + 100],
                    fill=(45, 45, 60, 255))
    draw.text((screen_margin + 30, appbar_y + 25), "📚 My Library", fill=(255, 255, 255, 255), font=font_title)
    
    # Book grid placeholder
    grid_y = appbar_y + 120
    book_colors = [
        (180, 80, 80), (80, 140, 80), (80, 80, 180),
        (180, 140, 60), (140, 80, 140), (60, 140, 140),
        (160, 100, 60), (100, 100, 160), (100, 160, 100),
    ]
    
    cols = 3
    gap = 20
    cover_w = (width - 2 * screen_margin - gap * (cols + 1)) // cols
    cover_h = int(cover_w * 1.5)
    
    for row in range(3):
        for col in range(3):
            idx = row * 3 + col
            if idx >= len(book_colors):
                break
            x = screen_margin + gap + col * (cover_w + gap)
            y = grid_y + row * (cover_h + 60)
            
            # Book cover
            draw.rounded_rectangle([x, y, x + cover_w, y + cover_h], radius=8,
                                    fill=book_colors[idx] + (255,))
            
            # Book title placeholder
            draw.rectangle([x, y + cover_h + 10, x + cover_w - 20, y + cover_h + 30],
                            fill=(150, 150, 170, 200))
            draw.rectangle([x, y + cover_h + 35, x + cover_w - 40, y + cover_h + 50],
                            fill=(120, 120, 140, 180))
    
    # Bottom nav bar
    nav_y = height - screen_margin - 100
    draw.rectangle([screen_margin, nav_y, width - screen_margin, nav_y + 80],
                    fill=(45, 45, 60, 255))
    nav_items = ["📚 Library", "📖 Reading", "📊 Stats", "⚙️ Settings"]
    nav_w = (width - 2 * screen_margin) // len(nav_items)
    for i, item in enumerate(nav_items):
        ix = screen_margin + i * nav_w + nav_w // 2 - 40
        color = (100, 180, 255, 255) if i == 0 else (150, 150, 170, 255)
        draw.text((ix, nav_y + 25), item, fill=color, font=font_small)
    
    return img


if __name__ == "__main__":
    # Generate Play Store icon (512x512)
    print("Generating Play Store icon (512x512)...")
    icon = create_playstore_icon(512)
    icon_path = os.path.join(WORKTREE, "playstore", "assets", "icon", "playstore_icon_512.png")
    icon.save(icon_path, "PNG")
    print(f"  Saved: {icon_path}")
    
    # Also save as the mipmap icons (replace placeholders)
    sizes = {'mipmap-mdpi': 48, 'mipmap-hdpi': 72, 'mipmap-xhdpi': 96, 
             'mipmap-xxhdpi': 144, 'mipmap-xxxhdpi': 192}
    for mipmap, sz in sizes.items():
        resized = icon.resize((sz, sz), Image.LANCZOS)
        mipmap_path = os.path.join("app", "src", "main", "res", mipmap, "ic_launcher.png")
        resized.save(mipmap_path, "PNG")
        print(f"  Updated: {mipmap_path} ({sz}x{sz})")
        # Also update round version
        mipmap_round_path = os.path.join("app", "src", "main", "res", mipmap, "ic_launcher_round.png")
        resized.save(mipmap_round_path, "PNG")
        print(f"  Updated: {mipmap_round_path} ({sz}x{sz})")
    
    # Generate feature graphic (1024x500)
    print("\nGenerating feature graphic (1024x500)...")
    feature = create_feature_graphic(1024, 500)
    feature_path = os.path.join(WORKTREE, "playstore", "assets", "feature_graphic.png")
    feature.save(feature_path, "PNG")
    print(f"  Saved: {feature_path}")
    
    # Generate phone screenshot mock
    print("\nGenerating phone screenshot mock...")
    screenshot = create_phone_screenshot_mock(1080, 1920)
    screenshot_path = os.path.join(WORKTREE, "playstore", "assets", "screenshots", "library_screen.png")
    screenshot.save(screenshot_path, "PNG")
    print(f"  Saved: {screenshot_path}")
    
    print("\nAll assets generated successfully!")
