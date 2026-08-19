classDiagram
    class Permiso {
        -id: Long
        -nombre: string
        -descripcion: string
    }

    class Rol {
        -id: Long
        -nombre: String
        -permisos: List~Permiso~
    }

    class Usuario {
        -id: Long
        -idRol: Long
        -nombre: string
        -email: string
        -telefono: string
        -passwordHash: string
        -habilitado: boolean
    }

    class Colegio {
        -id: Long
        -nombre: string
        -provincia: string
        -localidad: string
    }

    class EstadoPedido {
        <<enumeration>>
        PRESUPUESTADO
        SENADO
        LISTO_PARA_PRODUCCION
        CORTADO
        BORDADO
        CONFECCIONADO
        EN_CONTROL
        TERMINADO
        ENTREGADO
    }

    class HistorialEstadoPedido {
        -id: Long
        -idPedido: Long
        -estado: EstadoPedido
        -fechaCambio: Date
        -modificadoPorIdUsuario: Long
        -observaciones: String
    }

    class Pedido {
        -id: Long
        -idColegio: Long
        -estadoActual: EstadoPedido
        -idRepresentanteCurso: Long
        -codigoInterno: String
        -curso: string
        -cantAlumnos: int
        -observaciones: String
        -fechaCreacion: Date
        -fechaActualizacion: Date
        -creadoPorIdUsuario: Long
    }

    class Producto {
        -id: Long
        -idPedido: Long
        -tipoPrenda: String
        -cantidadTotal: int
        -costo: float
        -observaciones: string
        -imagenDisenoUrl: String
        -fechaCreacion: Date
        -fechaActualizacion: Date
    }

    class PrendaIndividual {
        -id: Long
        -idProducto: Long
        -talle: string
        -apodo: string
        -medidasIngresadas: JSON
    }

    class GuiaTalle {
        -id: Long
        -tipoPrenda: string
        -talle: string
        -medidaPechoMin: float
        -medidaPechoMax: float
        -medidaLargo: float
    }

    class FichaTecnica {
        -id: Long
        -idMolderia: Long
        -cantColores: int
        -cantBordados: int
        -cantApliques: int
        -tipografia: string
        -fechaCreacion: Date
        -fechaActualizacion: Date
    }

    class AtributoFicha {
        -id: Long
        -tipo: string
        -color: string
        -cantidadNecesaria: float
    }

    class MateriaPrima {
        -id: Long
        -nombre: string
        -tipo: string
        -color: string
        -cantidadEnStock: float
        -proveedor: string
    }

    class Molderia {
        -id: Long
        -numeroInterno: string
        -nombre: string
        -imagenUrl: String
    }

    class PiezaMolderia {
        -id: Long
        -nombrePieza: String
        -talle: string
        -geometriaPoligono: JSON
        -areaM2: float
    }

    %% Relaciones
    Permiso --o Rol : 1
    Rol "1" -- "0..*" Usuario
    
    Usuario "1" --> "0..*" Pedido : perteneceA (Rep. Curso)
    Usuario "1" --> "0..*" Pedido : gestiona (Vendedor)
    
    Colegio "1" -- "0..*" Pedido
    
    Pedido "1" *-- "1..*" HistorialEstadoPedido : tiene
    Pedido "1" *-- "1..*" Producto : contiene
    
    Producto "1" *-- "0..*" PrendaIndividual : se desglosa en
    Producto "1" -- "1" FichaTecnica : detalla confeccion
    
    FichaTecnica "1" *-- "1..*" AtributoFicha : compone
    
    %% Relación fuerte (FK) hacia la materia prima en stock
    AtributoFicha "0..*" --> "1" MateriaPrima : consume
    
    Molderia "1" -- "0..*" FichaTecnica : usada en
    Molderia "1" *-- "1..*" PiezaMolderia : dividida en
    
    %% GuiaTalle es un catálogo paramétrico para consulta general
    GuiaTalle .. PrendaIndividual : sirve de referencia