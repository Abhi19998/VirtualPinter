#!/bin/bash
sed -i 's/        LazyColumn(/        PullToRefreshBox(\n            isRefreshing = isRefreshing,\n            onRefresh = {\n                scope.launch {\n                    isRefreshing = true\n                    viewModel.syncFilesFromDisk(showMessage = true)\n                    delay(1000)\n                    isRefreshing = false\n                }\n            },\n            modifier = Modifier\n                .fillMaxSize()\n                .padding(innerPadding)\n        ) {\n            LazyColumn(/g' app/src/main/java/com/example/ui/PrinterDashboardScreen.kt

sed -i 's/                .padding(innerPadding)//g' app/src/main/java/com/example/ui/PrinterDashboardScreen.kt

