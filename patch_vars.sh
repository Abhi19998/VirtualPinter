#!/bin/bash
sed -i '84i\
    var isRefreshing by remember { mutableStateOf(false) }\
    val scope = rememberCoroutineScope()' app/src/main/java/com/example/ui/PrinterDashboardScreen.kt
