package jp.juggler.util.data

// arrayOf(string,boolean) などでkotlinコンパイラが警告を出すようになったので
// 警告のでないバリエーションを用意する
fun anyArrayOf(vararg values: Any): Array<out Any> = values
fun anyNullableArrayOf(vararg values: Any?): Array<out Any?> = values
