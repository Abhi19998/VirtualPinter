#!/bin/bash
mv app/src/main/java/com/example/ui/PrinterDashboardScreen.kt.bak app/src/main/java/com/example/ui/PrinterDashboardScreen.kt
sed -i 's/}    }}/}                }            }        }    }}/' app/src/main/java/com/example/ui/PrinterDashboardScreen.kt
