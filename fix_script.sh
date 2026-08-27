#!/bin/bash
sed -i '1567,1585c\            val targetDir = getVirtualPrinterFolder()\n            if (!targetDir.exists()) targetDir.mkdirs()\n            val targetFile = File(targetDir, fileName)\n            FileOutputStream(targetFile).use { it.write(scriptContent) }' app/src/main/java/com/example/utils/StorageHelper.kt
