# Inter Flash TV web

La app Android carga esta interfaz web. Inicio, TV en vivo, películas y series comparten el diseño oscuro y verde. Los catálogos de películas y series se obtienen del acceso Xtream de la lista configurada; una lista sin API compatible no proporciona esos catálogos.

`runtime.ts` es la función Bun de Railway. Lee `TV_DEFAULT_PLAYLIST_URL` del entorno; no guardar credenciales en el repositorio. Los endpoints de catálogo exigen la sesión aprobada de la lista y el origen de la aplicación. Los enlaces de reproducción y portadas usan tokens de sesión separados de los canales en vivo. Los catálogos se cargan por páginas de 48 títulos y se consultan con un límite de descarga de 20 MB.

La interfaz usa el reproductor existente. La compatibilidad de cada archivo, en especial MKV/AVI, depende del navegador o WebView; no hay transcodificación. No se agregan películas ni episodios ficticios cuando el proveedor no devuelve contenido.

## Verificación

Con Node 24 o superior, desde esta carpeta: `npm install` y `npm test`. Las pruebas usan respuestas simuladas; no conectan con el proveedor ni validan la reproducción de su contenido real.

Para publicar, subir `index.html` a GitHub y fijar `PAGE_URL` en `runtime.ts` al SHA de ese commit antes de actualizar la función. Conservar el origen y la variable del proveedor existentes.
