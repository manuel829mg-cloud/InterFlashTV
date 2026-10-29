# Inter Flash TV — actualizaciones sin desinstalar
## Estado
Preparado un **flujo manual de compilación release**, separado de las APK debug actuales. No se ha publicado ni distribuido ninguna versión estable.

## Cómo funciona
- La app nativa ya contiene un gestor que busca la última versión estable del repositorio GitHub configurado en `CoreBuildInfo.releaseRepo`, la descarga y usa el instalador de Android. Android TV puede requerir aprobación del usuario. No requiere usar Downloader en cada actualización.
- Las fuentes de canales se sincronizan con el panel por separado; una actualización de listas no necesita actualizar el APK.
- Los builds de prueba `assembleStandardDebug` pueden usar firmas temporales diferentes en cada ejecución. **No usar esas APK para prometer actualizaciones encima de la anterior**.
- Las versiones estables deben conservar el `applicationId` **do.interflash.tv**, una `versionCode` estrictamente creciente y **exactamente la misma clave de firma** que la versión ya instalada. Una firma nueva no sirve para actualizar las APK antiguas: si se perdió la clave original, Android exige una reinstalación de migración una sola vez. No borrar los datos existentes hasta preparar una migración o copia de seguridad.

## Paso de compilación
En la rama `feature/interflash-inplace-updates-test` existe `.github/workflows/build-signed-inplace-update.yml`. Solo se ejecuta manualmente (workflow_dispatch) **cuando ese workflow esté disponible en la rama predeterminada de GitHub** o después de incorporarlo a una rama que permita ejecutarlo. No altera la publicación estable.
El flujo exige los secretos del repositorio:
- `IF_TV_KEYSTORE_BASE64`: contenido base64 del keystore *original* de firma (nunca guardarlo en el repositorio).
- `IF_TV_KEYSTORE_PASSWORD`: contraseña del keystore.
- `IF_TV_KEY_ALIAS`: alias.
- `IF_TV_KEY_PASSWORD`: contraseña de la clave.

El flujo aborta si faltan secretos, usa `assembleStandardRelease`, comprueba la firma con `apksigner` y produce un artefacto **solo para prueba**. Evita escribir los secretos en logs.

## Antes de publicar para clientes
1. Comparar el SHA-256 del certificado de firma del APK instalado y del nuevo; verificar coincidencia.
2. Confirmar que el APK nuevo tiene `versionCode` superior a la instalada.
3. Instalar la nueva APK encima en TV de prueba, sin desinstalar, y verificar datos, vinculación y canales.
4. Publicar, bajo aprobación explícita, una versión **estable** de GitHub Releases con etiqueta semántica `v100.0.1` o superior, APK compatible y `latest` seleccionado. No marcar releases de prueba como `latest`.
5. Probar una **segunda** actualización interna para comprobar el flujo completo. No entregar a clientes hasta que pase la prueba.

**Nota:** la rama de prueba de GitHub contiene una app antigua WebView distinta. La app correcta es la OwnTV nativa compilada desde `feature/interflash-inplace-updates-test`. No trasladar el actualizador a la app antigua.
