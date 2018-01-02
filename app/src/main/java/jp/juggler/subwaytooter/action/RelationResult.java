package jp.juggler.subwaytooter.action;

import android.support.annotation.Nullable;

import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.table.UserRelation;

public class RelationResult {
	public @Nullable TootApiResult result;
	public @Nullable UserRelation relation;
}