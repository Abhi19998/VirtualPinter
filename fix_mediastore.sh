#!/bin/bash
sed -i 's/put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "\/VirtualPrinter")/put(MediaStore.MediaColumns.RELATIVE_PATH, "VirtualPrinter")/g' app/src/main/java/com/example/utils/StorageHelper.kt
sed -i 's/val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)/val downloadsDir = Environment.getExternalStorageDirectory()/g' app/src/main/java/com/example/utils/StorageHelper.kt
