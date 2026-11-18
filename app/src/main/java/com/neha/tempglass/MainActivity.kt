package com.neha.tempglass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.neha.tempglass.ui.PhirojTakriApp
import com.neha.tempglass.ui.theme.TempGlassTheme
import com.neha.tempglass.viewmodel.AddModelViewModel
import com.neha.tempglass.viewmodel.CasesViewModel
import com.neha.tempglass.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels {
        val app = application as TempGlassApplication
        MainViewModel.Factory(
            app.repository,
            app.caseRepository,
            app.brandRepository
        )
    }

    private val addModelViewModel: AddModelViewModel by viewModels {
        val app = application as TempGlassApplication
        AddModelViewModel.Factory(
            app.repository,
            app.brandRepository
        )
    }

    private val casesViewModel: CasesViewModel by viewModels {
        val app = application as TempGlassApplication
        CasesViewModel.Factory(
            app.caseRepository,
            app.brandRepository
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TempGlassTheme {
                PhirojTakriApp(
                    mainViewModel = mainViewModel,
                    addModelViewModel = addModelViewModel,
                    casesViewModel = casesViewModel
                )
            }
        }
    }
}