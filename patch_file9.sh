#!/bin/bash
sed -i 's/                            Text("Clear", style = MaterialTheme.typography.labelMedium)\n                    }/                            Text("Clear", style = MaterialTheme.typography.labelMedium)\n                        }\n                    }/' app/src/main/java/com/example/ui/PrinterDashboardScreen.kt
