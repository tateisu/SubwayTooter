package jp.juggler.subwaytooter.table

// SQLite にBooleanをそのまま保存することはできないのでInt型との変換が必要になる

// boolean to integer
fun Boolean.b2i() = if(this) 1 else 0

// integer to boolean
fun Int.i2b() = this!=0
