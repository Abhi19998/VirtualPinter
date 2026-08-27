#!/bin/bash
sed -i 's/            modifier = Modifier\n                .fillMaxSize()\n\n        ) {/            modifier = Modifier\n                .fillMaxSize()\n                .padding(innerPadding)\n        ) {/g' app/src/main/java/com/example/ui/PrinterDashboardScreen.kt
