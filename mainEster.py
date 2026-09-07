from shapely.geometry import Polygon # Sirve para crear formas geométricas.
from shapely.affinity import translate, rotate # Traslación y rotación
import matplotlib.pyplot as plt # Sirve para dibujar

# Creo un rectángulo
piece1 = Polygon([
    (0, 0),
    (50, 0),
    (50, 30),
    (0, 30)
])

print(piece1.area)

# Visualización

# Obtengo las coordenadas del borde
x, y = piece1.exterior.xy

# Dibujo el borde
plt.plot(x, y)

# Relleno el polígono
plt.fill(x, y, alpha=0.5)

# Límites del gráfico
plt.xlim(0, 100)
plt.ylim(0, 100)

# Cuadrícula
plt.grid()

# Mostrar gráfico
plt.show()

# Ahora: transformaciones geométricas. Veamos qué pasa si cambio la posición de la pieza
# Traslación

# Muevo la pieza
moved_piece1 = translate(piece1, xoff=20, yoff=40)

# Coordenadas de la pieza original
x1, y1 = piece1.exterior.xy

# Coordenadas de la pieza movida
x2, y2 = moved_piece1.exterior.xy

# Dibujo la pieza original
plt.plot(x1, y1)
plt.fill(x1, y1, alpha=0.5)

# Dibujo la pieza movida
plt.plot(x2, y2)
plt.fill(x2, y2, alpha=0.5)

# Límites del gráfico
plt.xlim(0, 120)
plt.ylim(0, 120)

# Cuadrícula
plt.grid()

# Mostrar gráfico
plt.show()


# Rotación
# Roto la pieza 180°
rotated_piece1 = rotate(piece1, 180)

# Coordenadas de la pieza rotada
x3, y3 = rotated_piece1.exterior.xy

# Creo una nueva figura
plt.figure()

# Dibujo la pieza rotada
plt.plot(x3, y3)
plt.fill(x3, y3, alpha=0.5)

# Límites del gráfico
plt.xlim(-60, 60)
plt.ylim(-60, 60)

# Cuadrícula
plt.grid()

# Mostrar gráfico
plt.show()


# =====================================
# OVERLAP / DETECCIÓN DE COLISIONES
# =====================================

# Creo la primera pieza
piece1 = Polygon([
    (0, 0),
    (50, 0),
    (50, 30),
    (0, 30)
])

# Creo la segunda pieza trasladada
piece2 = translate(piece1, xoff=20, yoff=10)

# Verifico overlap
print(piece1.intersects(piece2))

# Coordenadas de la primera pieza
x1, y1 = piece1.exterior.xy

# Coordenadas de la segunda pieza
x2, y2 = piece2.exterior.xy

# Nueva figura
plt.figure()

# Dibujo la primera pieza
plt.plot(x1, y1)
plt.fill(x1, y1, alpha=0.5)

# Dibujo la segunda pieza
plt.plot(x2, y2)
plt.fill(x2, y2, alpha=0.5)

# Límites del gráfico
plt.xlim(0, 100)
plt.ylim(0, 100)

# Cuadrícula
plt.grid()

# Mostrar gráfico
plt.show()

# En la terminal: True --> overlap
