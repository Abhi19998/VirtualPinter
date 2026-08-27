BEGIN { in_mediastore = 0 }
/^\s*\/\/ 2\. Also register in MediaStore/ { in_mediastore = 1 }
/^\s*\/\/ 3\./ { in_mediastore = 0 }
/^\s*\/\/ 4\./ { in_mediastore = 0 }
{ if (!in_mediastore) print $0 }
