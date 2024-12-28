package jp.juggler.subwaytooter.column

enum class ColumnLoadReason {
    PageSelect,
    RefreshAfterPost,
    TokenUpdated,
    OpenPush,
    ContentInvalidated,
    PullToRefresh,
    SettingChange,
    ForceReload,
}
