#!/bin/bash
sed -i '10a\
import androidx.compose.runtime.rememberCoroutineScope' app/src/main/java/com/example/ui/PrinterDashboardScreen.kt
