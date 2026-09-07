# Servicio de Geometría de Piezas

Microservicio FastAPI que calcula el contorno de una Pieza (área/ancho/largo/perímetro) a
partir de una definición por segmentos (recta / arco / círculo), y escala un contorno ya
calculado a un talle objetivo.

Este módulo reutiliza y adapta código desarrollado en conjunto con Romulo Hernanndez
Otaño y Martin Crespo para la materia Inteligencia Artificial, tag v1.0.0 del
repositorio https://github.com/RomaHerot/OptimizacionCorteTextil.

El código de ese repositorio vive sin modificaciones en `vendor/OptimizacionCorteTextil`
(traído con `git subtree`, fijado al tag `v1.0.0`) — por ahora el wrapper (`app/`) no
importa nada de ahí: ni el margen de costura (ya viene incluido en las coordenadas medidas
de la moldería) ni el margen de navegación de corte (`polyorder.create_margin_polygons`,
que pertenece a la futura fase de optimización de layout, `algorpatronsnap.py`, todavía no
integrada). Cuando se encare esa fase va a hacer falta volver a agregar el `sys.path.insert`
+ `import polyorder`/`algorpatronsnap` que hoy no está.

## Levantar el servicio

```bash
cd services/pieza-geometria
python -m venv .venv
.venv/Scripts/activate   # Windows
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8001
```

Swagger UI: http://localhost:8001/docs

## Endpoints

- `POST /piezas/calcular-base`: arma el contorno a partir de una lista de segmentos
  (RECTA / ARCO / CIRCULO), tesselando los tramos curvos, y devuelve área/ancho/largo/
  perímetro calculados directamente sobre ese polígono (sin ningún buffer/margen).
- `POST /piezas/escalar`: escala un contorno ya calculado a un ancho/largo objetivo
  (por talle), anclando el escalado en la esquina inferior izquierda del bounding box.
