package com.easy.htmltext.listener;

import android.text.Html;
import android.text.Spanned;

/**
 * Created by zhou on 16-7-27.
 */
public interface SpannedParser {

    Spanned parse(String source, Html.ImageGetter imageGetter);

}
