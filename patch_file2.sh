#!/bin/bash
sed -i 's/}                }            }        }    }}/}                }            }        }    }}/' app/src/main/java/com/example/ui/PrinterDashboardScreen.kt
sed -i '841,842s/    }//' app/src/main/java/com/example/ui/PrinterDashboardScreen.kt
sed -i '842,842s/}//' app/src/main/java/com/example/ui/PrinterDashboardScreen.kt
