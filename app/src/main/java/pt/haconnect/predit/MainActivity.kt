package pt.haconnect.predit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pt.haconnect.predit.ui.navigation.PreditApp
import pt.haconnect.predit.ui.theme.PreditTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PreditTheme {
                PreditApp()
            }
        }
    }
}
