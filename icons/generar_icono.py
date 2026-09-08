#!/usr/bin/env python3
"""
Generador de icono profesional para Asistente Pro.
Crea icons/icon.png (512x512) con degradado azul-morado y robot futurista.
"""

import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont


W = H = 512
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DESTINO = os.path.join(ROOT, "icons", "icon.png")


def normalizar(p):
    """Devuelve una coordenada entre 0 y 1 dado un valor 0-255."""
    return (p - 128) / 128.0


def buscar_fuente():
    """Busca una fuente disponible en el sistema."""
    candidatas = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
    ]
    for f in candidatas:
        if os.path.exists(f):
            return f
    return None


def degradado(draw):
    """Dibuja un degradado lineal azul -> morado."""
    c_sup = (60, 40, 120)      # azul profundo
    c_inf = (140, 60, 220)     # morado

    for y in range(H):
        t = y / float(H - 1)
        r = int(c_sup[0] + (c_inf[0] - c_sup[0]) * t)
        g = int(c_sup[1] + (c_inf[1] - c_sup[1]) * t)
        b = int(c_sup[2] + (c_inf[2] - c_sup[2]) * t)
        draw.line([(0, y), (W, y)], fill=(r, g, b))


def dibujar_robot(draw, cx, cy, escala=1.0):
    """Dibuja un robot blanco futurista con cabeza y ojos brillantes."""
    s = escala * 0.9
    cabeza = int(150 * s)
    cuerpo = int(140 * s)

    # --- Cabeza (rectángulo redondeado) ---
    x0 = int(cx - cabeza // 2)
    y0 = int(cy - 120 * s)
    x1 = int(cx + cabeza // 2)
    y1 = int(cy + 40 * s)
    draw.rounded_rectangle([x0, y0, x1, y1], radius=30 * s,
                           fill=(245, 245, 250))

    # Antenas
    draw.line([(cx, y0), (cx, y0 - int(35 * s))], fill=(220, 220, 235), width=6)
    draw.ellipse([cx - int(14 * s), y0 - int(60 * s),
                  cx + int(14 * s), y0 - int(32 * s)],
                 fill=(0, 229, 255))

    # --- Visor (cristal) ---
    vx0 = int(cx - cabeza // 2 + 25 * s)
    vy0 = int(cy - 100 * s)
    vx1 = int(cx + cabeza // 2 - 25 * s)
    vy1 = int(cy - 10 * s)
    draw.rounded_rectangle([vx0, vy0, vx1, vy1], radius=22 * s,
                           fill=(30, 30, 55))

    # --- Ojos brillantes (cian) ---
    o_r = 14 * s
    oy = int(cy - 58 * s)
    draw.ellipse([int(cx - 35 * s) - int(o_r), oy - int(o_r),
                  int(cx - 35 * s) + int(o_r), oy + int(o_r)],
                 fill=(0, 229, 255))
    draw.ellipse([int(cx + 35 * s) - int(o_r), oy - int(o_r),
                  int(cx + 35 * s) + int(o_r), oy + int(o_r)],
                 fill=(0, 229, 255))
    # Brillo de ojos
    draw.ellipse([int(cx - 41 * s), oy - int(20 * s),
                  int(cx - 29 * s), oy - int(8 * s)],
                 fill=(200, 255, 255))
    draw.ellipse([int(cx + 29 * s), oy - int(20 * s),
                  int(cx + 41 * s), oy - int(8 * s)],
                 fill=(200, 255, 255))

    # Sonrisa
    draw.arc([int(cx - 25 * s), int(cy + 18 * s),
              int(cx + 25 * s), int(cy + 42 * s)],
             start=30, end=150, fill=(0, 229, 255), width=4)

    # --- Cuerpo ---
    bx0 = int(cx - cuerpo // 2)
    by0 = int(cy + 55 * s)
    bx1 = int(cx + cuerpo // 2)
    by1 = int(cy + 185 * s)
    draw.rounded_rectangle([bx0, by0, bx1, by1], radius=28 * s,
                           fill=(230, 230, 240))

    # Panel central
    draw.rounded_rectangle([int(cx - 55 * s), int(cy + 75 * s),
                            int(cx + 55 * s), int(cy + 125 * s)],
                           radius=14 * s, fill=(40, 40, 70))

    # Luces del panel
    luz_x = [int(cx - 35 * s), int(cx), int(cx + 35 * s)]
    colores_luz = [(0, 229, 255), (124, 128, 255), (3, 218, 198)]
    for i, lx in enumerate(luz_x):
        draw.ellipse([lx - int(7 * s), int(cy + 87 * s),
                      lx + int(7 * s), int(cy + 101 * s)],
                     fill=colores_luz[i])

    # Brazos (laterales)
    draw.rounded_rectangle([bx0 - int(30 * s), int(cy + 55 * s),
                            bx0, int(cy + 130 * s)],
                           radius=12 * s, fill=(210, 210, 225))
    draw.rounded_rectangle([bx1, int(cy + 55 * s),
                            bx1 + int(30 * s), int(cy + 130 * s)],
                           radius=12 * s, fill=(210, 210, 225))


def main():
    os.makedirs(os.path.dirname(DESTINO), exist_ok=True)

    imagen = Image.new("RGB", (W, H))
    draw = ImageDraw.Draw(imagen)

    degradado(draw)
    draw_robot = ImageDraw.Draw(imagen)
    dibujar_robot(draw_robot, W // 2, H // 2)

    # --- Resplandor suave global ---
    imagen = imagen.filter(ImageFilter.GaussianBlur(radius=1))
    draw = ImageDraw.Draw(imagen)
    dibujar_robot(draw, W // 2, H // 2)

    # --- Texto inferior ---
    fuente = buscar_fuente()
    if fuente:
        try:
            font_grande = ImageFont.truetype(fuente, 34)
        except Exception:
            font_grande = ImageFont.load_default()

        texto = "ASISTENTE PRO"
        bbox = draw.textbbox((0, 0), texto, font=font_grande)
        ancho = bbox[2] - bbox[0]
        alto = bbox[3] - bbox[1]
        pos_x = (W - ancho) // 2
        pos_y = H - alto - 30
        draw.text((pos_x + 2, pos_y + 2), texto, font=font_grande,
                  fill=(0, 0, 0, 120))
        draw.text((pos_x, pos_y), texto, font=font_grande,
                  fill=(255, 255, 255))

    imagen.save(DESTINO, "PNG")
    print(f"✅ Icono creado en {DESTINO} ({W}x{H})")


if __name__ == "__main__":
    main()