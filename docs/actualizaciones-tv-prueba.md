# Actualizaciones de Inter Flash TV — diseño de prueba

Estado: **diseño preparado; función aún no implementada ni validada en televisores**.

## Objetivo
Reducir al mínimo las descargas manuales sin interrumpir la reproducción ni afectar a clientes existentes.

## Dos clases de cambios
1. Canales, enlaces y contenidos de la web: obtenerlos del servidor al abrir y periódicamente, con caché y recuperación ante errores. No cambiar los enlaces activos durante la reproducción.
2. Cambios del APK/reproductor: comprobar un manifiesto HTTPS con `versionCode`, `versionName`, `apkUrl`, `sha256`, `mandatory` y `releaseNotes`. No instalar silenciosamente.

## Requisitos de seguridad
- Canal `test` separado del canal `stable`; el cliente nunca recibe la versión de prueba por accidente.
- Comprobar que el dominio de descarga sea el esperado, que el APK esté firmado por la misma clave, y verificar SHA-256 del archivo descargado.
- Rechazar versiones con `versionCode` menor o igual a la instalada.
- No mostrar avisos ni iniciar descargas mientras se reproduce un canal; informar al volver al inicio.
- Android TV: usar instalador del sistema y consentimiento cuando corresponda. No prometer actualización silenciosa de APK fuera de una tienda/administrador compatible.
- Conservar preferencias, favoritos y acceso de cliente. Permitir posponer una actualización no crítica.
- Pruebas en varios Android TV/Google TV y dispositivos de baja memoria antes de distribución.
- Samsung Tizen y LG webOS usan paquetes y mecanismos de actualización diferentes: no se actualizan con APK Android.

## Antes de programar
Identificar MainActivity, reproductor, origen del catálogo de canales, clave de firma, repositorio de publicación y destino del manifiesto. Construir una implementación compatible con el SDK actual y realizar prueba instalando una actualización por encima de una versión anterior.

## Criterios de aceptación
- En ausencia de actualización: abrir normal sin demora ni ventanas.
- Con nueva versión: aviso claro y descarga/inicio de instalador solo tras interacción apropiada.
- Si falta conexión o servidor: reproducción anterior intacta.
- Tras actualización: arranque, favoritos, listas y reproducción intactos.
- Sin efectos sobre la versión estable de los clientes hasta aprobación.
