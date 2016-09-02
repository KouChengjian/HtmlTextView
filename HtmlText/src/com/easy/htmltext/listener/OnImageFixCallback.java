package com.easy.htmltext.listener;

import com.easy.htmltext.ImageHolder;


/**
 * Created by zhou on 16-5-28.
 */
public interface OnImageFixCallback {
    /**
     * 修复图片尺寸的方法
     *
     * @param holder     ImageHolder对象
     * @param imageReady 图片是否已经加载完毕
     */
    void onFix(ImageHolder holder, boolean imageReady);
}
