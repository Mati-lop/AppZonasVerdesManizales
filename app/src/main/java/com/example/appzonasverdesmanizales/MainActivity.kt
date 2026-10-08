package com.example.appzonasverdesmanizales

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.util.Locale

// 1. ESTRUCTURA PARA REPRESENTAR UNA ZONA VERDE
data class ZonaVerde(
    val nombre: String,
    val latitud: Double,
    val longitud: Double,
    val radioMetros: Double
)

class MainActivity : AppCompatActivity() {

    // Controles de la interfaz
    lateinit var tvCoordenadas: TextView
    lateinit var tvDireccion: TextView
    lateinit var btnBuscar: Button
    lateinit var mapView: MapView

    // Cliente de localización de Google
    lateinit var fusedLocationClient: FusedLocationProviderClient

    // Marcador del usuario en el mapa
    private var marcadorUsuario: Marker? = null

    // Callback del GPS (null mientras no se ha iniciado el rastreo)
    private var locationCallBack: LocationCallback? = null

    // 2. LISTA DE ZONAS VERDES
    // revisar las coordenadas en Google Maps
    // Ajustar el rango para que el círculo quede sobre el parque
    //Lista de las Zonas verdes
    private val listaZonasVerdes = listOf(
        ZonaVerde("Ecoparque Alcazares Arenillo", 5.064574664408418, -75.53228230427133, 700.0),
        ZonaVerde("Ecoparque Los Yarumos", 5.062883356645205, -75.48236142799412, 350.0),
        ZonaVerde("Monumento a Los Colonizadores", 5.076967441875805, -75.52779873310736, 220.0),
        ZonaVerde("Bosque Popular El Prado", 5.035116049802072, -75.47785938167235, 700.0),
        ZonaVerde("Recinto del Pensamiento", 5.040281331685337, -75.44530244659926, 800.0),
        ZonaVerde("Parque El Control", 5.030363461393618, -75.45663064773618, 60.0),
        ZonaVerde("Parque La Piedra", 5.031371133596407, -75.46100206358791, 70.0),
        ZonaVerde("Parque Escondido", 5.030552118825955, -75.46202033197835, 75.0),
        ZonaVerde("Parque Floriblanca", 5.032304614336903, -75.4636290848865, 75.0),
        ZonaVerde("Parque 33B", 5.032784863128226, -75.4657617790859, 50.0),
        ZonaVerde("Parque la Enea", 5.031808040316168, -75.46729870255368, 140.0),
        ZonaVerde("Parque Lusitania", 5.03029654482733, -75.47711057655411, 75.0),
        ZonaVerde("Parque Aranjuez", 5.042638534892344, -75.49945456147526, 90.0),
        ZonaVerde("Parque Malabar Parte Alta", 5.0462807453863245, -75.50083980734424, 50.0),
        ZonaVerde("Parque Malabar", 5.045665072887258, -75.50363691589517, 90.0),
        ZonaVerde("Parque de La Isabela", 5.055947824983744, -75.50574159551248, 90.0),
        ZonaVerde("Parque Eucaliptos", 5.05658945393458, -75.50246894075292, 50.0),
        ZonaVerde("Parque Faneón", 5.061293530509805, -75.50030838741158, 100.0),
        ZonaVerde("Parque La Leonora", 5.064065822729088, -75.49507427315594, 100.0),
        ZonaVerde("Parque de la Mujer", 5.06527764214103, -75.49935890397325, 70.0),
        ZonaVerde("Parque Ernesto Gutiérrez", 5.068330787647474, -75.51214885839934, 100.0),
        ZonaVerde("Parque el Colibrí", 5.069326647901237, -75.48651382882343, 100.0),
        ZonaVerde("Parque Villa Café", 5.067558513179728, -75.48548942554162, 100.0),
    )

    // Última zona notificada (evita repetir la notificación mientras sigue dentro)
    private var zonaActualNotificada: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // CONFIGURACIÓN DE OSMDROID (debe ir ANTES de setContentView, porque el mapa
        // se crea al cargar el layout y ahí lee el user agent)
        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences("osmdroid", MODE_PRIVATE)
        )

        // User agent propio y fijo: OpenStreetMap lo exige para no bloquear la app (403)
        Configuration.getInstance().userAgentValue =
            "AppZonasVerdesManizales/1.0 (matias.lopez60307@ucaldas.edu.co)"

        setContentView(R.layout.activity_main)

        tvCoordenadas = findViewById(R.id.tvCoordenadas)
        tvDireccion = findViewById(R.id.tvDireccion)
        btnBuscar = findViewById(R.id.btn_buscar)
        mapView = findViewById(R.id.mapView)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        crearCanalNotificacion()
        pedirPermisoNotificaciones()

        // FUENTE DEL MAPA
        // true  = OpenStreetMap, siguiendo su política de uso (URL oficial, user agent propio
        //         y atribución visible).
        // false = Esri, como alternativa si OpenStreetMap llegara a bloquear la app.
        val usarOpenStreetMap = true

        if (usarOpenStreetMap) {
            mapView.setTileSource(
                XYTileSource(
                    "OSM", 0, 19, 256, ".png",
                    arrayOf("https://tile.openstreetmap.org/"),
                    "© OpenStreetMap contributors"
                )
            )
        } else {
            val mapaEsri = object : OnlineTileSourceBase(
                "EsriCalles", 0, 19, 256, ".png",
                arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/"),
                "Powered by Esri"
            )
            {
                override fun getTileURLString(pMapTileIndex: Long): String {
                    return baseUrl +
                            MapTileIndex.getZoom(pMapTileIndex) + "/" +
                            MapTileIndex.getY(pMapTileIndex) + "/" +
                            MapTileIndex.getX(pMapTileIndex)
                }
            }
            mapView.setTileSource(mapaEsri)
        }

        // Texto de atribución visible en el mapa (lo exigen OpenStreetMap y Esri)
        mapView.overlays.add(CopyrightOverlay(this))

        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(15.0)

        // Centrar el mapa en la primera zona mientras no hay GPS
        val primera = listaZonasVerdes.first()
        mapView.controller.setCenter(GeoPoint(primera.latitud, primera.longitud))

        dibujarZonasVerdes()

        btnBuscar.setOnClickListener { iniciarRastreo() }
    }

    // Dibuja el círculo y el marcador de cada zona verde
    private fun dibujarZonasVerdes() {
        for (zona in listaZonasVerdes) {
            val puntoCentro = GeoPoint(zona.latitud, zona.longitud)

            val circulo = Polygon(mapView)
            circulo.points = Polygon.pointsAsCircle(puntoCentro, zona.radioMetros)
            circulo.fillPaint.color = 0x3300FF00
            circulo.outlinePaint.color = Color.GREEN
            circulo.outlinePaint.strokeWidth = 3f
            circulo.title = zona.nombre
            mapView.overlays.add(circulo)

            val marcadorZona = Marker(mapView)
            marcadorZona.position = puntoCentro
            marcadorZona.title = zona.nombre
            marcadorZona.icon = ContextCompat.getDrawable(this, R.drawable.red)
            mapView.overlays.add(marcadorZona)
        }
        mapView.invalidate()
    }

    private fun iniciarRastreo() {
        val permiso = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (permiso != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100
            )
            return
        }

        // Centrar el mapa en la última ubicación conocida
        fusedLocationClient.lastLocation.addOnSuccessListener { ubicacion ->
            if (ubicacion != null) {
                mapView.controller.setCenter(GeoPoint(ubicacion.latitude, ubicacion.longitude))
                mostrarDireccion(ubicacion)
            }
        }

        seguimientoGPS()
    }

    // Obtiene la dirección con Geocoder
    private fun mostrarDireccion(ubicacion: Location) {
        val geocoder = Geocoder(this, Locale.getDefault())
        try {
            val direcciones = geocoder.getFromLocation(ubicacion.latitude, ubicacion.longitude, 1)
            if (!direcciones.isNullOrEmpty()) {
                tvDireccion.text = direcciones[0].getAddressLine(0)
            }
        } catch (e: Exception) {
            tvDireccion.text = "No se pudo obtener la dirección"
        }
    }

    // Seguimiento GPS continuo
    private fun seguimientoGPS() {
        // Si ya está activo, no se crea otro (evita duplicados al presionar el botón varias veces)
        if (locationCallBack != null) return

        val permiso = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (permiso != PackageManager.PERMISSION_GRANTED) return

        val solicitarUbicacion = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
            .setMinUpdateIntervalMillis(1000)
            .build()

        locationCallBack = object : LocationCallback() {
            override fun onLocationResult(resultado: LocationResult) {
                val ubicacion = resultado.lastLocation ?: return
                nuevoPuntoUsuario(ubicacion)
                verificarIngresoAZonasVerdes(ubicacion)
            }
        }

        fusedLocationClient.requestLocationUpdates(solicitarUbicacion, locationCallBack!!, mainLooper)
    }

    // Actualiza o crea el marcador del usuario
    private fun nuevoPuntoUsuario(ubicacion: Location) {
        val puntoUsuario = GeoPoint(ubicacion.latitude, ubicacion.longitude)

        if (marcadorUsuario == null) {
            marcadorUsuario = Marker(mapView)
            marcadorUsuario!!.title = "Tu ubicación actual"
            marcadorUsuario!!.icon = ContextCompat.getDrawable(this, R.drawable.blue)
            mapView.overlays.add(marcadorUsuario)
        }

        marcadorUsuario!!.position = puntoUsuario
        mapView.invalidate()
    }

    // 3. DETECCIÓN: revisa todas las zonas y avisa si el usuario está dentro de alguna
    private fun verificarIngresoAZonasVerdes(ubicacion: Location) {
        var zonaEncontrada: ZonaVerde? = null
        var distanciaZona = 0f

        for (zona in listaZonasVerdes) {
            val resultado = FloatArray(1)
            Location.distanceBetween(
                zona.latitud, zona.longitud,
                ubicacion.latitude, ubicacion.longitude,
                resultado
            )

            if (resultado[0] <= zona.radioMetros) {
                zonaEncontrada = zona
                distanciaZona = resultado[0]
                break
            }
        }

        if (zonaEncontrada != null) {
            tvCoordenadas.text = "Latitud: ${ubicacion.latitude}\nLongitud: ${ubicacion.longitude}\n" +
                    "¡Estás en: ${zonaEncontrada.nombre}! (a %.1f m del centro)".format(distanciaZona)

            // Solo notifica si es una zona distinta a la última notificada
            if (zonaActualNotificada != zonaEncontrada.nombre) {
                zonaActualNotificada = zonaEncontrada.nombre
                mostrarNotificacion(zonaEncontrada.nombre)
            }
        } else {
            // Salió de las zonas: se reinicia para que vuelva a notificar al entrar de nuevo
            zonaActualNotificada = null
            tvCoordenadas.text = "Latitud: ${ubicacion.latitude}\nLongitud: ${ubicacion.longitude}\nFuera de zonas verdes"
        }
    }

    // 4. NOTIFICACIÓN CON EL NOMBRE DEL PARQUE
    private fun mostrarNotificacion(nombreZona: String) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val permiso = ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            if (permiso != PackageManager.PERMISSION_GRANTED) return
        }

        val notificacion = NotificationCompat.Builder(this, "ZONA_SEGURA")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("¡Zona Verde Detectada!")
            .setContentText("Has ingresado a: $nombreZona")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1001, notificacion)
    }

    // Pide el permiso de notificaciones al abrir la app (Android 13+)
    private fun pedirPermisoNotificaciones() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val permiso = ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            if (permiso != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 200
                )
            }
        }
    }

    // Cuando el usuario acepta el permiso de ubicación, el rastreo inicia solo
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            iniciarRastreo()
        }
    }

    // Crear el canal de notificación (Android 8+)
    private fun crearCanalNotificacion() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                "ZONA_SEGURA",
                "Alertas de zonas verdes",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(canal)
        }
    }

    // Ciclo de vida: el mapa y el GPS se detienen cuando la app no está visible
    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCallBack?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallBack = null
    }
}