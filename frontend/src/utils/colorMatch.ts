/**
 * Matching de color puro (RGB → color de paleta más cercano por distancia euclídea).
 * Separado del modal de gotero a propósito para poder reusarlo desde una futura
 * detección automática por clustering de imagen, sin duplicar esta lógica.
 */

export interface ColorRgb {
  r: number;
  g: number;
  b: number;
}

export function hexARgb(hex: string): ColorRgb {
  const limpio = hex.replace('#', '');
  return {
    r: parseInt(limpio.substring(0, 2), 16),
    g: parseInt(limpio.substring(2, 4), 16),
    b: parseInt(limpio.substring(4, 6), 16),
  };
}

export function rgbAHex({ r, g, b }: ColorRgb): string {
  const componente = (n: number) => n.toString(16).padStart(2, '0');
  return `#${componente(r)}${componente(g)}${componente(b)}`.toUpperCase();
}

export function distanciaEuclidea(a: ColorRgb, b: ColorRgb): number {
  return Math.sqrt((a.r - b.r) ** 2 + (a.g - b.g) ** 2 + (a.b - b.b) ** 2);
}

/** Devuelve el elemento de `paleta` cuyo `hex` está más cerca de `rgb`, o null si la paleta está vacía. */
export function colorMasCercano<T extends { hex: string }>(rgb: ColorRgb, paleta: T[]): T | null {
  let mejor: T | null = null;
  let mejorDistancia = Infinity;

  for (const color of paleta) {
    const distancia = distanciaEuclidea(rgb, hexARgb(color.hex));
    if (distancia < mejorDistancia) {
      mejorDistancia = distancia;
      mejor = color;
    }
  }

  return mejor;
}
