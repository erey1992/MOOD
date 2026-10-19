# MOOD

Reproductor Android de colecciones de MP3.

## Descargar audio de YouTube

1. Pulsa el icono de descarga de la barra superior.
2. Pega el enlace de una canción o de una lista de reproducción de YouTube / YouTube Music. También puedes compartir el enlace hacia MOOD.
3. Pulsa **Elegir carpeta** y concede acceso con el selector de Android. Puedes seleccionar almacenamiento interno o una tarjeta SD disponible. Android restringe algunas carpetas raíz; en ese caso, elige o crea una subcarpeta.
4. Pulsa **Añadir a descargas**. Se crea una tarjeta por canción con título, autor cuando está disponible, duración, estado, porcentaje y barra de descarga. Se procesan hasta tres canciones simultáneamente; la conversión a MP3 se hace de una en una para limitar el uso de CPU. Puedes añadir otros enlaces mientras se descarga, cancelar canciones individuales o cancelar toda la cola.

MOOD recuerda la carpeta elegida y permite cambiarla para los siguientes enlaces; cada tarjeta conserva su carpeta original. El MP3 se añade a las colecciones al finalizar. Si el archivo ya existe, la tarjeta ofrece **Omitir** o **Reemplazar**, sin crear una copia numerada. Al reemplazar, prepara el MP3 nuevo antes de tocar el anterior. Un fallo no detiene las demás canciones: **Reintentar** vuelve a poner solo esa canción en la cola. Las descargas continúan al cerrar la pantalla; si Android finaliza el proceso, se conserva la cola y las canciones interrumpidas ofrecen reintento al abrir MOOD.

Desde YouTube o YouTube Music, pulsa **Compartir → MOOD**: se abre la cola con el enlace y la última carpeta precargados. Confirma con **Añadir a descargas**, o cambia la carpeta antes. Un enlace de reproducción con `v=` se interpreta como canción individual; un enlace `/playlist?list=…` añade la lista completa. Se eliminan canciones repetidas dentro de la misma lista y no se encola otra vez una canción que ya esté activa en el mismo destino. Las listas deben ser accesibles sin iniciar sesión en MOOD. Las colecciones se actualizan después de cada MP3 guardado; **Ver colecciones** muestra la biblioteca actualizada.

El audio se descarga con [youtubedl-android / yt-dlp](https://github.com/yausername/youtubedl-android), versión 0.18.1, y se convierte a MP3 con [FFmpegKit Audio](https://github.com/ffmpegkit-maintained/ffmpeg), versión 8.1.9, compatible con páginas de memoria de 16 KB. El APK admite dispositivos **ARM64 y x86_64** con Android 7 o superior. Sus componentes nativos aumentan el tamaño del APK. El motor intenta actualizarse una vez al día antes de descargar; si no puede actualizarse, utiliza la versión instalada. La disponibilidad depende del video y de la respuesta de YouTube.

## Compilar y verificar

Con JDK 17 o superior y Android SDK 35:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.
