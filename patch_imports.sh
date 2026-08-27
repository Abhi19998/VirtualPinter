#!/bin/bash
sed -i '9a\
import androidx.compose.material3.pulltorefresh.PullToRefreshBox\
import kotlinx.coroutines.delay\
import kotlinx.coroutines.launch' app/src/main/java/com/example/ui/PrinterDashboardScreen.kt
