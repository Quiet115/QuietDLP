# Estado · QuietDLP · 2026-11-15 · Bogotá

Siguiente paso: completar y comprobar la ronda común autorizada, empezando por la seguridad del perfil.

## Alcance y aceptación

Encargo de Sebas, transmitido el 2026-11-15: controles de etapas 0–2 y mejoras comunes pendientes;
continuidad entre bloques, sin migrar la app ni ampliar sus funciones específicas.

| Ficha | Criterio observable | Estado inicial comprobado |
|---|---|---|
| T5 / etapas 0–1 | Guía breve para compilar/probar; estado y cambios conservados en rama | Guía y estado añadidos; 16 cambios previos eran solo comentarios, comprobados con KotlinLexer y guardados en `3306c3c` |
| Perfil / etapa 2, §11 | Importación validada, sin referencias de archivos ajenos ni secretos; exportación aceptada por el importador; faltantes con valores predeterminados | Fallos pendientes en referencias locales, duplicados y límites; se corrigen antes de pulir la interfaz |
| Ajustes y buscador | Pestañas en orden; registro enlazado con destinos; búsqueda EN/ES sin acentos | Ya existen; pendiente contraste de cobertura del registro |
| Paletas e idioma | Pruebas de contraste y paridad EN/ES; modo del sistema con Obsidian/Paper | Ya existen; falta comprobación de integración actual |
| Aviso / §6, §12 | 20 s por arranque, cierre y rotación; apagar avisos respeta comprobación manual | Existe; captura obsoleta de `busy` y respuesta tardía requieren corrección |
| Notas | Conversor común probado; todas las vistas lo utilizan | Existe; contrastar referencia y pruebas |
| Permisos | Lista según API; secuencia sin repetir, estados al volver y tras recrear | Existe; estado de secuencia no se conserva al recrear |

## Foto y ficha de salud

Base leída: `3e6bb2d` (Claude: importación y tiempos). Rama de trabajo:
`codex/common-improvements-20261115`. Aplicación 0.5.1, código 35, mínimo Android 24,
objetivo/compilación 35, arm64, Kotlin 2.0.21, Compose, JDK 17.

1. Git y copia externa: Git local y remoto existente comprobados; sincronización de esta rama pendiente.
2. Cambios antiguos: 16 archivos de comentarios preservados en commit separado; ninguna modificación descartada.
3. Pruebas: `testDebugUnitTest --offline --rerun-tasks --console=plain`, 292 pruebas,
   43 suites, cero fallos/errores el 2026-11-15. Compilación de Kotlin incluida; APK aún sin comprobar.
4. Guía y estado: creados en esta ronda; validación final pendiente.
5. Operaciones destructivas: auditoría parcial; pendientes externos al perfil siguen abajo. No afirmar etapa 2 completa.
6. README/Funciones/notas: contraste en curso; versión publicada solo atribuida a los documentos de Claude.
7. Requisitos base: tabla anterior; no se declara cumplimiento total de interfaz sin teléfono.
8. Dispositivo real: sin comprobar en esta ronda; no autorizado para estas pruebas.
9. Accesibilidad/paridad: pruebas automatizadas existentes; TalkBack y nuevas opciones de movimiento aplazados por Sebas.
10. Firma/SHA/LICENSE: LICENSE presente; pruebas del actualizador existentes. No se ha generado ni publicado paquete firmado.

## Seguridad: comprobado y pendientes

- Claude ya limita los ids de páginas seguidas y genera ids nuevos al restaurar; el parseo de tiempos
  grandes ya no desborda. La importación ya informa de excepciones: cambios de `3e6bb2d`, no de esta ronda.
- Perfil opcional con historial: las URI importadas aún llegan a las acciones sobre archivos;
  los ids repetidos llegan a las claves de Compose. Reproducción sintética previa, sin borrar archivos reales.
- Importación de ajustes incompleta: los ausentes conservan el valor anterior, contrario al estándar.
- Exportación: límites aún no simétricos para entradas grandes. Comprobar antes de abrir el destino.
- Visto y no tocado: cookies entre dominios, pendientes de páginas seguidas por encima de 500,
  escrituras de vistos/pendientes y actualización interrumpida del motor. Revisión anterior en el intercambio
  con Claude; no son funciones comunes y requieren su propia reproducción y arreglo. No están resueltos por esta ronda.

## Restricciones y entrega

No releases, instalaciones, datos reales, servicios, cambios en otras apps ni estándares compartidos.
Guía de compilación en `CLAUDE.md`. Informe detallado de esta ronda y del trabajo previo pendiente de
anexar a `C:\AI\Chat entre Claude y ChatGPT\QuietDLP\2026-11-12 1211 - Revisión adicional de código.md`.
