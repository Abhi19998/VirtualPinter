#!/bin/bash
sed -i '878,886d' app/src/main/java/com/example/ui/PrinterDashboardScreen.kt
sed -i 's/}    }}/}                }            }        }    }}/' app/src/main/java/com/example/ui/PrinterDashboardScreen.kt
sed -i '$d' app/src/main/java/com/example/ui/PrinterDashboardScreen.kt
