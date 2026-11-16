package io.github.ebziboi.delta

import android.os.Bundle
import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import io.github.ebziboi.delta.ui.AppViewModel
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.ebziboi.delta.ui.DeltaRoot

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if(savedInstanceState==null) intent.getStringExtra("food_route")?.let {ViewModelProvider(this)[AppViewModel::class.java].openFood(it)}
        setContent { DeltaRoot() }
    }
    override fun onNewIntent(intent:Intent) {
        super.onNewIntent(intent);setIntent(intent)
        intent.getStringExtra("food_route")?.let {ViewModelProvider(this)[AppViewModel::class.java].openFood(it)}
    }
}
