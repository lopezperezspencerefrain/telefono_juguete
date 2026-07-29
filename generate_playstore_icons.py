import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

# Target specifications for Google Play Store icon: 512x512 32-bit PNG
SIZE = 512
OUTPUT_DIR = "/home/spencer/.gemini/antigravity/brain/558390c6-591e-4bad-a252-03b2dfa5079e"

def create_gradient_bg(size, color1, color2):
    base = Image.new("RGBA", (size, size), color1)
    top = Image.new("RGBA", (size, size), color2)
    mask = Image.new("L", (size, size))
    mask_draw = ImageDraw.Draw(mask)
    for y in range(size):
        alpha = int(255 * (y / size))
        mask_draw.line([(0, y), (size, y)], fill=alpha)
    base.paste(top, (0, 0), mask)
    return base

# --- OPCIÓN 1: TELÉFONO MÁGICO CANDY ---
def generate_option_1():
    img = create_gradient_bg(SIZE, (255, 107, 139, 255), (112, 161, 255, 255))
    draw = ImageDraw.Draw(img)

    # Decorative stars in background
    star_color = (255, 255, 255, 60)
    for cx, cy, r in [(70, 80, 25), (440, 90, 35), (80, 420, 30), (430, 430, 20)]:
        draw.ellipse([cx-r, cy-r, cx+r, cy+r], fill=star_color)

    # Toy phone body (white rounded card)
    phone_box = [110, 60, 402, 452]
    # Shadow
    shadow = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    s_draw = ImageDraw.Draw(shadow)
    s_draw.rounded_rectangle([115, 68, 407, 460], radius=50, fill=(0, 0, 0, 70))
    shadow = shadow.filter(ImageFilter.GaussianBlur(12))
    img = Image.alpha_composite(img, shadow)
    draw = ImageDraw.Draw(img)

    # Outer phone body
    draw.rounded_rectangle(phone_box, radius=50, fill=(255, 255, 255, 255), outline=(255, 225, 235, 255), width=6)
    
    # Phone screen inner frame
    screen_box = [135, 90, 377, 420]
    draw.rounded_rectangle(screen_box, radius=36, fill=(245, 247, 255, 255))

    # Screen Header Banner
    draw.rounded_rectangle([150, 105, 362, 155], radius=20, fill=(255, 126, 185, 255))
    
    # Keypad Grid (6 colorful buttons)
    btn_colors = [
        (255, 107, 107, 255), (72, 219, 251, 255), (254, 202, 87, 255),
        (29, 209, 161, 255), (155, 89, 182, 255), (255, 159, 243, 255)
    ]
    
    positions = [
        (160, 175), (231, 175), (302, 175),
        (160, 245), (231, 245), (302, 245)
    ]
    
    for (x, y), col in zip(positions, btn_colors):
        # button shadow
        draw.rounded_rectangle([x, y+3, x+50, y+53], radius=16, fill=(0, 0, 0, 30))
        # button main
        draw.rounded_rectangle([x, y, x+50, y+50], radius=16, fill=col)
        # button highlight
        draw.rounded_rectangle([x+4, y+4, x+46, y+20], radius=10, fill=(255, 255, 255, 80))

    # Big Green Call Button
    call_box = [170, 320, 342, 395]
    draw.rounded_rectangle([170, 324, 342, 399], radius=25, fill=(16, 140, 100, 255))
    draw.rounded_rectangle(call_box, radius=25, fill=(16, 172, 132, 255))
    draw.rounded_rectangle([180, 324, 332, 350], radius=15, fill=(255, 255, 255, 70))

    # Save PNG
    path = os.path.join(OUTPUT_DIR, "opcion1_telefono_magico.png")
    img.save(path, "PNG")
    print(f"Generado: {path}")

# --- OPCIÓN 2: OSITO Y BOTÓN DE LLAMADA ---
def generate_option_2():
    img = create_gradient_bg(SIZE, (72, 219, 251, 255), (255, 159, 243, 255))
    draw = ImageDraw.Draw(img)

    # Glowing background circle
    draw.ellipse([50, 50, 462, 462], fill=(255, 255, 255, 50))

    # Big Call Button Base
    shadow = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    s_draw = ImageDraw.Draw(shadow)
    s_draw.ellipse([90, 100, 422, 432], fill=(0, 0, 0, 80))
    shadow = shadow.filter(ImageFilter.GaussianBlur(16))
    img = Image.alpha_composite(img, shadow)
    draw = ImageDraw.Draw(img)

    # Main Red/Pink Phone Badge
    draw.ellipse([90, 90, 422, 422], fill=(255, 82, 123, 255))
    draw.ellipse([105, 105, 407, 407], fill=(255, 107, 139, 255))
    # Glass top shine
    draw.chord([105, 105, 407, 300], start=180, end=360, fill=(255, 255, 255, 60))

    # Teddy Bear Ears
    draw.ellipse([120, 60, 200, 140], fill=(254, 202, 87, 255), outline=(255, 255, 255, 255), width=5)
    draw.ellipse([140, 80, 180, 120], fill=(255, 159, 243, 255))

    draw.ellipse([312, 60, 392, 140], fill=(254, 202, 87, 255), outline=(255, 255, 255, 255), width=5)
    draw.ellipse([332, 80, 372, 120], fill=(255, 159, 243, 255))

    # White Center Badge
    draw.ellipse([150, 150, 362, 362], fill=(255, 255, 255, 255))
    
    # Inner Phone receiver icon in green
    draw.ellipse([175, 175, 337, 337], fill=(16, 172, 132, 255))
    draw.ellipse([185, 185, 327, 260], fill=(255, 255, 255, 50))

    # Save PNG
    path = os.path.join(OUTPUT_DIR, "opcion2_osito_telefono.png")
    img.save(path, "PNG")
    print(f"Generado: {path}")

# --- OPCIÓN 3: TECLADO 3D COLORIDO ---
def generate_option_3():
    img = create_gradient_bg(SIZE, (254, 202, 87, 255), (255, 107, 107, 255))
    draw = ImageDraw.Draw(img)

    # 3D Keyboard Base Container
    shadow = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    s_draw = ImageDraw.Draw(shadow)
    s_draw.rounded_rectangle([75, 75, 442, 442], radius=60, fill=(0, 0, 0, 70))
    shadow = shadow.filter(ImageFilter.GaussianBlur(14))
    img = Image.alpha_composite(img, shadow)
    draw = ImageDraw.Draw(img)

    # Keyboard body
    draw.rounded_rectangle([70, 65, 442, 437], radius=60, fill=(255, 255, 255, 245), outline=(255, 230, 210, 255), width=6)

    # 9 Vibrant 3D Buttons
    colors = [
        (255, 107, 129), (90, 200, 250), (255, 159, 243),
        (84, 160, 255), (29, 209, 161), (254, 202, 87),
        (155, 89, 182), (255, 126, 185), (72, 219, 251)
    ]
    
    grid = [
        (100, 95),  (211, 95),  (322, 95),
        (100, 206), (211, 206), (322, 206),
        (100, 317), (211, 317), (322, 317)
    ]

    for (x, y), c in zip(grid, colors):
        # 3D shadow bottom
        draw.rounded_rectangle([x, y+6, x+90, y+96], radius=24, fill=(c[0]-30, max(0, c[1]-30), max(0, c[2]-30), 255))
        # Main key cap
        draw.rounded_rectangle([x, y, x+90, y+90], radius=24, fill=(c[0], c[1], c[2], 255))
        # Top highlight curve
        draw.rounded_rectangle([x+6, y+6, x+84, y+40], radius=16, fill=(255, 255, 255, 90))

    # Save PNG
    path = os.path.join(OUTPUT_DIR, "opcion3_teclado_3d.png")
    img.save(path, "PNG")
    print(f"Generado: {path}")

if __name__ == "__main__":
    generate_option_1()
    generate_option_2()
    generate_option_3()
