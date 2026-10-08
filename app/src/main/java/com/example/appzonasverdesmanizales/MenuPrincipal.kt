package com.example.appzonasverdesmanizales

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MenuPrincipal : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu_principal)

        val btnIrAlMapa: Button = findViewById(R.id.btnIrAlMapa)

        // Abre la pantalla del mapa
        btnIrAlMapa.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }
}