import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
VENDOR = RAIZ / "vendor" / "OptimizacionCorteTextil"

for ruta in (RAIZ, VENDOR):
    if str(ruta) not in sys.path:
        sys.path.insert(0, str(ruta))
