#!/bin/bash
awk '/isRefreshing = isRefreshing,/ { p2r = 1 }
/modifier = Modifier/ && p2r == 1 { p2rmod = 1 }
/fillMaxSize\(\)/ && p2rmod == 1 { print; print "                .padding(innerPadding)"; p2r = 0; p2rmod = 0; next }
{print}' app/src/main/java/com/example/ui/PrinterDashboardScreen.kt > temp.kt && mv temp.kt app/src/main/java/com/example/ui/PrinterDashboardScreen.kt
