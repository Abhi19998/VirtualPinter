#!/bin/bash
sed -i 's/val extDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)//g' app/src/main/java/com/example/utils/StorageHelper.kt
sed -i 's/val pubDir = File(extDownloads, "VirtualPrinter")/val pubDir = getVirtualPrinterFolder()/g' app/src/main/java/com/example/utils/StorageHelper.kt
sed -i 's/File(extDownloads, VIRTUAL_PRINTER_FOLDER_NAME).mkdirs()/getVirtualPrinterFolder().mkdirs()/g' app/src/main/java/com/example/utils/StorageHelper.kt
sed -i 's/val extFile = File(File(extDownloads, "VirtualPrinter"), name)/val extFile = File(getVirtualPrinterFolder(), name)/g' app/src/main/java/com/example/utils/StorageHelper.kt
sed -i 's/val extFile = File(File(extDownloads, "VirtualPrinter"), cleanName)/val extFile = File(getVirtualPrinterFolder(), cleanName)/g' app/src/main/java/com/example/utils/StorageHelper.kt
