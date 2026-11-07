#!/usr/bin/env python3
"""Gráficas de evidencia de results.md, desde el registro de eventos.

  l04-latencias.svg   un punto por archivo pequeño, una franja por ejecución de L04,
                      sobre un eje logarítmico común con la línea de 3 s de ASR-L04
  d01-pendientes.svg  los archivos en espera durable durante D-1: suben con la caída
                      del RNIC y bajan a cero en la ventana nocturna

Toma la última ejecución válida de cada escenario. Escribe SVG estáticos en
docs/assets/resultados/graficas/. Uso: python3 analisis/graficas.py
"""
import math
import subprocess
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
DESTINO = RAIZ / "docs" / "assets" / "resultados" / "graficas"

# Paleta de referencia (skill dataviz), tema claro del wiki. Una sola serie por
# gráfica: las filas se nombran con texto, no con color.
SUPERFICIE, TINTA, TINTA_2, TINTA_3 = "#fcfcfb", "#0b0b0b", "#52514e", "#8a8985"
REJILLA, SERIE, UMBRAL, FRANJA = "#e6e5e1", "#2a78d6", "#c2410c", "#fdecec"
FUENTE = "font-family='system-ui, -apple-system, Segoe UI, sans-serif'"


def consulta(sql):
    r = subprocess.run(["docker", "compose", "-f", str(RAIZ / "deploy/docker-compose.yml"), "exec", "-T", "postgres",
                        "psql", "-U", "reto4", "-d", "reto4", "-qtA", "-F", "\t", "-c", sql],
                       check=True, capture_output=True, text=True)
    return [linea.split("\t") for linea in r.stdout.splitlines() if linea.strip()]


def ultima(prefijo):
    filas = consulta(f"SELECT id FROM registro.corrida WHERE id LIKE '{prefijo}-2%' AND valida AND cierre IS NOT NULL "
                     f"ORDER BY arranque DESC LIMIT 1")
    return filas[0][0] if filas else None


def texto(x, y, t, tam=12, color=TINTA_2, ancla="start", peso="normal"):
    return (f"<text x='{x:.1f}' y='{y:.1f}' {FUENTE} font-size='{tam}' fill='{color}' text-anchor='{ancla}' "
            f"font-weight='{peso}'>{t}</text>")


def svg(ancho, alto, cuerpo, titulo):
    return (f"<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 {ancho} {alto}' width='{ancho}' height='{alto}' "
            f"role='img' aria-label='{titulo}'><rect width='100%' height='100%' fill='{SUPERFICIE}'/>{''.join(cuerpo)}</svg>\n")


def l04():
    filas = []
    for prefijo, nombre in (("l2", "L-2 · sin masivos"), ("l1", "L-1 · 5 masivos"), ("l4-6", "L-4 · 6 masivos"),
                            ("l4-8", "L-4 · 8 masivos"), ("l4-10", "L-4 · 10 masivos"), ("l3", "L-3 · 5 masivos, sin carriles")):
        c = ultima(prefijo)
        if not c:
            continue
        # Los mismos pequeños que mide l04.sql: con masivos en proceso, o todos si no hay masivos.
        vals = consulta(f"""
            WITH w AS (SELECT * FROM registro.corrida WHERE id = '{c}'),
            m AS (SELECT min(a.recepcion_inicio) d, max(a.resultado_en) h, count(*) n FROM registro.archivos a, w
                  WHERE a.tipo = 'masivo' AND a.enviado BETWEEN w.arranque AND w.cierre)
            SELECT coalesce(a.latencia_ms, extract(epoch FROM w.cierre - a.recibido) * 1000)
            FROM registro.archivos a, w, m
            WHERE a.tipo = 'pequeno' AND a.caso = 'valido' AND a.estado_acuse = 'ACUSADO'
              AND a.recibido BETWEEN w.inicio AND w.fin AND (m.n = 0 OR a.recibido BETWEEN m.d AND m.h)""")
        v = sorted(max(1.0, float(x[0])) for x in vals)
        if v:
            filas.append((nombre, v))
    if not filas:
        return None
    ancho, izq, der, arriba, fila_h = 960, 230, 40, 70, 46
    alto = arriba + fila_h * len(filas) + 60
    lo, hi = 10, max(10_000, max(max(v) for _, v in filas) * 1.2)
    xs = lambda ms: izq + (math.log10(ms) - math.log10(lo)) / (math.log10(hi) - math.log10(lo)) * (ancho - izq - der)
    c = [texto(20, 28, "ASR-L04 · latencia de cada archivo pequeño, del último byte al resultado definitivo", 15, TINTA, peso="600"),
         texto(20, 48, "Un punto por archivo, en los minutos con masivos en proceso. Eje logarítmico. La raya vertical de cada franja es su p99.", 12)]
    for t in (10, 30, 100, 300, 1000, 3000, 10000, 30000, 100000):
        if t <= hi:
            x = xs(t)
            c.append(f"<line x1='{x:.1f}' x2='{x:.1f}' y1='{arriba - 6}' y2='{alto - 50}' stroke='{REJILLA}'/>")
            c.append(texto(x, alto - 32, f"{t / 1000:g} s" if t >= 1000 else f"{t} ms", 11, TINTA_3, "middle"))
    xu = xs(3000)
    c.append(f"<line x1='{xu:.1f}' x2='{xu:.1f}' y1='{arriba - 10}' y2='{alto - 50}' stroke='{UMBRAL}' stroke-width='2' stroke-dasharray='6 4'/>")
    c.append(texto(xu + 6, arriba - 14, "umbral 3 s", 11, UMBRAL))
    for i, (nombre, v) in enumerate(filas):
        y = arriba + i * fila_h + fila_h / 2
        p99 = v[min(len(v) - 1, math.ceil(0.99 * len(v)) - 1)]
        c.append(texto(20, y - 2, nombre, 12, TINTA))
        c.append(texto(20, y + 14, f"n = {len(v)} · p99 = {p99 / 1000:.2f} s", 11, TINTA_3))
        paso = max(1, len(v) // 1500)
        for j, ms in enumerate(v[::paso]):
            jit = ((j * 7919) % 17 - 8) * 1.3
            c.append(f"<circle cx='{xs(ms):.1f}' cy='{y + jit:.1f}' r='2.2' fill='{SERIE}' fill-opacity='0.35'/>")
        c.append(f"<line x1='{xs(p99):.1f}' x2='{xs(p99):.1f}' y1='{y - 16}' y2='{y + 16}' stroke='{TINTA}' stroke-width='2'/>")
    return svg(ancho, alto, c, "Latencia de los archivos pequeños por ejecución de L04")


def d01():
    c_id = ultima("d1")
    if not c_id:
        return None
    w = consulta(f"SELECT extract(epoch FROM arranque), extract(epoch FROM cierre) FROM registro.corrida WHERE id = '{c_id}'")[0]
    t0, t1 = float(w[0]), float(w[1])
    # Pendientes en cada instante: entran con el primer «archivo.pendiente» y salen con su resultado.
    ev = consulta(f"""
        SELECT extract(epoch FROM p.ts), 1 FROM registro.evento p
        WHERE p.tipo = 'archivo.pendiente' AND p.datos->>'componente' <> 'reprocesador'
          AND p.ts BETWEEN to_timestamp({t0}) AND to_timestamp({t1})
        UNION ALL
        SELECT extract(epoch FROM r.ts), -1 FROM registro.evento r
        WHERE r.tipo = 'archivo.resultado' AND r.datos->>'componente' = 'reprocesador'
          AND r.ts BETWEEN to_timestamp({t0}) AND to_timestamp({t1})
        ORDER BY 1""")
    fallas = consulta(f"SELECT extract(epoch FROM falla), extract(epoch FROM coalesce(restablecida, to_timestamp({t1}))) "
                      f"FROM registro.fallas WHERE falla BETWEEN to_timestamp({t0}) AND to_timestamp({t1})")
    ventanas = consulta(f"SELECT extract(epoch FROM inicio), numero FROM registro.ventanas WHERE inicio BETWEEN to_timestamp({t0}) AND to_timestamp({t1})")
    puntos, n = [(t0, 0)], 0
    for ts, d in ev:
        n += int(d)
        puntos.append((float(ts), n))
    puntos.append((t1, n))
    ancho, alto, izq, der, arriba, abajo = 960, 380, 70, 30, 70, 50
    maxy = max(10, max(p[1] for p in puntos) * 1.15)
    xs = lambda t: izq + (t - t0) / (t1 - t0) * (ancho - izq - der)
    ys = lambda v: alto - abajo - v / maxy * (alto - arriba - abajo)
    c = [texto(20, 28, "ASR-D01 · archivos en espera durable durante D-1", 15, TINTA, peso="600"),
         texto(20, 48, "Suben mientras el RNIC está caído (franja) y bajan a cero en la ventana nocturna (raya azul). Ninguno se rechaza.", 12)]
    for fa, fb in fallas:
        c.append(f"<rect x='{xs(float(fa)):.1f}' y='{arriba}' width='{xs(float(fb)) - xs(float(fa)):.1f}' height='{alto - arriba - abajo}' fill='{FRANJA}'/>")
        c.append(texto(xs(float(fa)) + 6, arriba + 16, "RNIC caído", 11, UMBRAL))
    for k in range(0, 6):
        v = maxy / 5 * k
        c.append(f"<line x1='{izq}' x2='{ancho - der}' y1='{ys(v):.1f}' y2='{ys(v):.1f}' stroke='{REJILLA}'/>")
        c.append(texto(izq - 8, ys(v) + 4, f"{v:.0f}", 11, TINTA_3, "end"))
    for m in range(0, int((t1 - t0) // 60) + 1, 5):
        c.append(texto(xs(t0 + m * 60), alto - abajo + 18, f"{m} min", 11, TINTA_3, "middle"))
    for vt, num in ventanas:
        x = xs(float(vt))
        c.append(f"<line x1='{x:.1f}' x2='{x:.1f}' y1='{arriba}' y2='{alto - abajo}' stroke='{SERIE}' stroke-width='2' stroke-dasharray='4 3'/>")
        c.append(texto(x - 6, arriba + 32, f"ventana {num}", 11, SERIE, "end"))
    # Escalonado: cada cambio avanza en horizontal hasta su instante y luego sube o baja.
    camino = f"M{xs(puntos[0][0]):.1f} {ys(puntos[0][1]):.1f}" + "".join(
        f" H{xs(t):.1f} V{ys(v):.1f}" for t, v in puntos[1:])
    c.append(f"<path d='{camino}' fill='none' stroke='{TINTA}' stroke-width='2'/>")
    pico = max(p[1] for p in puntos)
    c.append(texto(izq + 6, ys(pico) - 8, f"máximo: {pico} archivos pendientes", 11, TINTA))
    return svg(ancho, alto, c, "Archivos pendientes durante D-1")


if __name__ == "__main__":
    DESTINO.mkdir(parents=True, exist_ok=True)
    for nombre, f in (("l04-latencias", l04), ("d01-pendientes", d01)):
        contenido = f()
        if contenido:
            (DESTINO / f"{nombre}.svg").write_text(contenido, encoding="utf-8")
            print(f"{nombre}.svg")
        else:
            print(f"{nombre}: sin ejecuciones válidas todavía")
