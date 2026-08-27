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
        MainViewModel.Factory(
            (application as TempGlassApplication).repository,
            (application as TempGlassApplication).caseRepository
        )
    }

    private val addModelViewModel: AddModelViewModel by viewModels {
        AddModelViewModel.Factory((application as TempGlassApplication).repository)
    }

    private val casesViewModel: CasesViewModel by viewModels {
        CasesViewModel.Factory((application as TempGlassApplication).caseRepository)
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