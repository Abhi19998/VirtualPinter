#!/bin/bash
awk 'NR==308{print "        }"; print} NR!=308{print}' app/src/main/java/com/example/ui/PrinterDashboardScreen.kt > temp.kt && mv temp.kt app/src/main/java/com/example/ui/PrinterDashboardScreen.kt
