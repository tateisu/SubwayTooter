package jp.juggler.emoji;

import android.util.SparseArray;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class EmojiMap {

    public static final int CATEGORY_PEOPLE = 0;
    public static final int CATEGORY_NATURE = 1;
    public static final int CATEGORY_FOODS = 2;
    public static final int CATEGORY_ACTIVITY = 3;
    public static final int CATEGORY_PLACES = 4;
    public static final int CATEGORY_OBJECTS = 5;
    public static final int CATEGORY_SYMBOLS = 6;
    public static final int CATEGORY_FLAGS = 7;
    public static final int CATEGORY_OTHER = 8;


    public static class Category {
        public final int category_id; // this is String resource id;
        public final ArrayList< String > emoji_list = new ArrayList<>();

        public Category( int category_id ){
            this.category_id = category_id;
        }
    }

    public static class EmojiResource {
        @DrawableRes
        public final int drawableId;
        @Nullable
        public final String assetsName;

        public EmojiResource( @DrawableRes int drawableId ){
            this.drawableId = drawableId;
            this.assetsName = null;
        }

        EmojiResource( @NonNull String assetsName ){
            this.drawableId = 0;
            this.assetsName = assetsName;
        }

        public boolean isSvg(){
            return assetsName != null;
        }
    }

    public static class EmojiInfo {
        @NonNull public final String unified;
        @NonNull public final EmojiResource er;

        public EmojiInfo( @NonNull String unified, @NonNull EmojiResource er ){
            this.unified = unified;
            this.er = er;
        }
    }



    // 表示に使う。絵文字のユニコードシーケンスから画像リソースIDへのマップ
    public int utf16MaxLength;

    public final HashMap< String, EmojiResource> utf16ToEmojiResource = new HashMap<>();

    public final EmojiTrie<EmojiResource> utf16Trie = new EmojiTrie<>();

    // 表示と投稿に使う。絵文字のショートコードから画像リソースIDとユニコードシーケンスへのマップ
    public final HashMap< String, EmojiInfo> shortNameToEmojiInfo = new HashMap<>();

    // 入力補完に使う。絵文字のショートコードのソートされたリスト
    public final ArrayList< String > shortNameList = new ArrayList<>();

    // ピッカーに使う。カテゴリのリスト
    public final SparseArray<Category> categoryMap = new SparseArray<>();

    // 素の数字とcopyright,registered, trademark は絵文字にしない
    private boolean isIgnored( @NonNull String code ){
        int c = code.charAt( 0 );
        return code.length() == 1 && c <= 0xae || c == 0x2122;
    }

    // PNG
//	private void code( @NonNull String code, @DrawableRes int resId ){
//		if( isIgnored( code ) ) return;
//        EmojiResource res = new EmojiResource( resId );
//        utf16ToEmojiResource.put( code, res);
//        utf16Trie.append(code,0,res);
//	}

    // Assets
    void code(@NonNull String code, @NonNull String assetsName ){
        if( isIgnored( code ) ) return;
        EmojiResource res = new EmojiResource( assetsName );
        utf16ToEmojiResource.put( code, res);
        utf16Trie.append(code,0,res);
    }

    void name( @NonNull String name, @NonNull String unified ){
        if( isIgnored( unified ) ) return;
        EmojiResource er = utf16ToEmojiResource.get( unified );
        if( er == null ) throw new IllegalStateException( "missing emoji for code " + unified );
        shortNameToEmojiInfo.put( name, new EmojiInfo( unified, er ) );
        shortNameList.add( name );
    }

    void category(@StringRes int string_id, @NonNull String name ){
        EmojiMap.Category c = categoryMap.get( string_id );
        if( c == null ){
            c = new EmojiMap.Category( string_id );
            categoryMap.put( string_id, c );
        }
        c.emoji_list.add( name );
    }

    private EmojiMap(){
        EmojiMapInitializer.initAll(this);
        Collections.sort( shortNameList );
    }

    public static final EmojiMap sMap = new EmojiMap();

    //////////////////////////////////////////////////////

    public boolean isStartChar(char c) {
        return utf16Trie.hasNext(c);
    }

}

