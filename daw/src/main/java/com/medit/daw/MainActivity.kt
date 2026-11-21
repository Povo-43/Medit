package com.medit.daw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.medit.daw.ui.DawScreen
import com.medit.shared.editor.EditorViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel: EditorViewModel = ViewModelProvider(this)[EditorViewModel::class.java]
        setContent {
            DawScreen(viewModel)
        }
    }
}
