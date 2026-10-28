# Inter Flash TV — control de suscripciones (revisión previa a implementación)

## Hallazgos verificados
- La app Android `MainActivity.java` llama `POST /api/tv-native/register` y `GET /api/tv-native/config` en `https://interflash-app-production.up.railway.app`.
- La app guarda `device_token` y `playlist_revision` localmente; al aprobarse descarga M3U y la entrega al reproductor web `inter-tv-prueba`.
- En el código `interflash-gui-n-app` consultado no aparecen implementaciones de `/api/tv-native/register` ni `/api/tv-native/config`. **No desplegar cambios de autorización hasta localizar el backend real**.
- La lista M3U ya entregada puede continuar en memoria del reproductor, por lo que negar la siguiente descarga no es suficiente para revocar el acceso.

## Contrato de servicio propuesto
- Una suscripción de TV por cliente y un único token de dispositivo vinculado.
- TV e internet con estados de facturación independientes.
- RD$600/mes; corte individual según primer pago; abonos anticipados suman meses a fecha pagada; al pagar tras mora se mantiene el día de corte original.
- Suspensión automática el día de corte impago, reactivación al registrar pago, sin acumular cobros por meses suspendidos.
- Prueba opcional de 24 horas, una sola por cliente.
- A los tres meses consecutivos de impago, desvincular el token y enviar aviso WhatsApp el mismo día; conservar historial y pagos.
- Recordatorio WhatsApp un día antes del corte.

## Controles necesarios antes de producción
1. Identificar y respaldar la implementación y la base de datos reales de `tv-native`; no asumir que el repositorio visible coincide con el servidor.
2. Añadir estado de suscripción, fecha de vencimiento, día de corte, historial de pagos, y desvinculación idempotente por dispositivo.
3. Exigir autorización vigente para cada reproducción o entregar URLs de streaming de corta duración validadas en servidor. **No exponer URL M3U permanente al cliente** si se pretende suspensión efectiva.
4. En la app, al recibir suspensión explícita, detener reproducción, limpiar estado sensible y mostrar «Servicio suspendido por falta de pago». No borrar datos ante errores temporales de red.
5. Pruebas con televisor de prueba: suspensión, restauración, adelanto de pago, vencimiento, tres meses impagos, internet independiente y ausencia de efectos sobre otros dispositivos.
6. Desplegar solo tras revisión y aprobación explícita, con respaldo y plan de reversión.

**Estado:** documento de análisis, sin cambio funcional ni despliegue.
