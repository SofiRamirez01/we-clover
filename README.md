Objetivo del programa: Encontrar la forma más eficiente de ordenar y posicionar patrones en un lienzo, de forma tal que se use la menor cantidad posible de tela

Utilización:
1) Abrir OptCorteTextil
2) Crear polígonos en base a los patrones de ropa a crear
3) Transformar a patrones y señalar la cantidad de patrones para añadir al lienzo
4) Añadir al lienzo y encontrar la posible mejor posición para los patrones seleccionados

Cromosoma: Los genes son la ubicación x/y y orientación de cada patrón, como un arreglo de tuplas

Función de aptitud: Da un puntaje respecto a varios factores con diferentes pesos:
*  Lo compacto que quedan los patrones
*  Distancias entre patrones
*  Distancia de cada patrón a la base del lienzo
*  Máxima cantidad de lienzo
*  Patrones que toquen la base del lienzo
*  Patrones que se tocan
*  Patrones simétricos que tocan los costados del lienzo

 También se penalizan severamente:
*  Superposición de patrones
*  Patrones que salen del lienzo
  
Tipo de selección: Por torneo

Valores iniciales del algoritmo:
 * Población inicial (100)
 * Generaciones (200)
 * Frecuencia de mutación en x (0.3)
 * Frecuencia de mutación en y (0.99)
 * Escala de mutación en x (200)
 * Escala de mutación positiva en y (1)
 * Escala de mutación negativa en y (500)
 * Frecuencia de mutación de orientación (0.15)
 * Tamaño del torneo (10)
 * Probabilidad de cruzamiento (0.9)
   
Notas de la mutación:
*  La escala de mutación negativa en y es proporcional a la distancia del patrón a la base del lienzo, con el propósito de acelerar la búsqueda de soluciones
*  Si una mutación cambia la posición de un patrón de forma que el patrón queda superpuesto o fuera del lienzo, el cambio de posición no se realizara en el eje que cause el problema
  
Notas del cruzamiento:
 * El cruzamiento genera una mascara aleatoria, creando dos individuos en base a los genes de sus padres
  
Ejemplo de posiciones de patrones en un lienzo final:
<img width="1536" height="754" alt="Algorsnap3" src="https://github.com/user-attachments/assets/092fbf68-a280-41e0-8492-245e4ce99bd1" />

Evolución a través de las generaciones del ejemplo:
<img width="1536" height="754" alt="Algorsnap3gens" src="https://github.com/user-attachments/assets/67880d5e-afd6-4a33-90e4-b7d00df86d9c" />

