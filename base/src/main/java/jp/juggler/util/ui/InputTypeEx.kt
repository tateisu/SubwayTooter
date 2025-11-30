package jp.juggler.util.ui

import android.text.InputType

@Suppress("unused", "ConstPropertyName")
object InputTypeEx {

    // For entering a date.
    const val date = InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_DATE

    // For entering a date and time.
    const val datetime = InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_NORMAL

    // There is no content type. The text is not editable.
    const val none = 0

    // A numeric only field.
    const val number = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_NORMAL

    // Can be combined with number and its other options to allow a decimal (fractional) number.
    const val numberDecimal = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL

    // A numeric password field. .
    const val numberPassword =
        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD

    // Can be combined with number and its other options to allow a signed number.
    const val numberSigned = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED

    // For entering a phone number.
    const val phone = InputType.TYPE_CLASS_PHONE

    // Just plain old text.
    const val text = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL

    // Can be combined with text and its variations to
    // specify that this field will be doing its own auto-completion
    // and talking with the input method appropriately.
    const val textAutoComplete = text or InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE

    // Can be combined with text and its variations to
    // request auto-correction of text being input.
    const val textAutoCorrect = text or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT

    // Can be combined with text and its variations to
    // request capitalization of all characters.
    const val textCapCharacters = text or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS

    // Can be combined with text and its variations to
    // request capitalization of the first character of every sentence.
    const val textCapSentences = text or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

    // Can be combined with text and its variations to
    // request capitalization of the first character of every word.
    const val textCapWords = text or InputType.TYPE_TEXT_FLAG_CAP_WORDS

    // Text that will be used as an e-mail address.
    const val textEmailAddress =
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

    // Text that is being supplied as the subject of an e-mail.
    const val textEmailSubject =
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT

    // Text that is filtering some other data.
    const val textFilter = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_FILTER

    // Can be combined with text and its variations to indicate that though
    // the regular text view should not be multiple lines,
    // the IME should provide multiple lines if it can.
    const val textImeMultiLine = text or InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE

    // Text that is the content of a long message.
    const val textLongMessage =
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE

    // Can be combined with text and its variations to allow multiple lines of text in the field.
    // If this flag is not set, the text field will be constrained to a single line.
    const val textMultiLine = text or InputType.TYPE_TEXT_FLAG_MULTI_LINE

    // Can be combined with text and its variations to indicate that
    // the IME should not show any dictionary-based word suggestions.
    const val textNoSuggestions = text or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

    // Text that is a password.
    const val textPassword = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

    // Text that is the name of a person.
    const val textPersonName =
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME

    // Text that is for phonetic pronunciation, such as a phonetic name field in a contact entry.
    const val textPhonetic = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PHONETIC

    // Text that is being supplied as a postal mailing address.
    const val textPostalAddress =
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS

    // Text that is the content of a short message.
    const val textShortMessage =
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE

    // Text that will be used as a URI.
    const val textUri = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI

    // Text that is a password that should be visible.
    const val textVisiblePassword =
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD

    // Text that is being supplied as text in a web form.
    const val textWebEditText =
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT

    // Text that will be used as an e-mail address on a web form.
    const val textWebEmailAddress =
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS

    // Text that will be used as a password on a web form.
    const val textWebPassword =
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD

    // For entering a time.
    const val time = InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME
}
