package com.easy.htmltext;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.ImageSpan;
import android.view.View;
import android.widget.TextView;

import com.bumptech.glide.BitmapTypeRequest;
import com.bumptech.glide.GenericRequestBuilder;
import com.bumptech.glide.GifTypeRequest;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.target.Target;
import com.easy.htmltext.listener.Html2SpannedParser;
import com.easy.htmltext.listener.LongClickableSpan;
import com.easy.htmltext.listener.OnImageClickListener;
import com.easy.htmltext.listener.OnImageFixCallback;
import com.easy.htmltext.listener.OnImageLongClickListener;
import com.easy.htmltext.listener.OnURLClickListener;
import com.easy.htmltext.listener.OnUrlLongClickListener;
import com.easy.htmltext.listener.SpannedParser;

public class HtmlText implements Drawable.Callback , View.OnAttachStateChangeListener {

	private static Pattern IMAGE_TAG_PATTERN = Pattern.compile("<img(.*?)>");
	private static Pattern IMAGE_SRC_PATTERN = Pattern.compile("src=\"(.*?)\"");
    private static Pattern IMAGE_WIDTH_PATTERN = Pattern.compile("width=\"(.*?)\"");
    private static Pattern IMAGE_HEIGHT_PATTERN = Pattern.compile("height=\"(.*?)\"");
	
    private Drawable placeHolder, errorImage;
    private SoftReference<HashSet<Target>>  targets;
    private HashMap<String, ImageHolder> mImages;
    private HashSet<GifDrawable> gifDrawables;
    
    private SpannedParser spannedParser;
    private OnImageFixCallback mImageFixCallback;
    private OnImageClickListener onImageClickListener;//图片点击回调
    private OnImageLongClickListener onImageLongClickListener; // 图片长按回调
    private OnUrlLongClickListener onUrlLongClickListener; // 链接长按回调
    private OnURLClickListener onURLClickListener;//超链接点击回调
	
    private boolean autoFix;
    private boolean async;
    private boolean noImage;
    private String richText;
    private TextView textView;
    private int placeHolderRes = -1, errorImageRes = -1;
    
    private HtmlText() {
        this(true, false, null, new ColorDrawable(Color.LTGRAY), new ColorDrawable(Color.GRAY));
    }
    
    private HtmlText(boolean autoFix, boolean async, String richText, Drawable placeHolder, Drawable errorImage) {
        this.autoFix = autoFix;
        this.async = async;
        this.richText = richText;
        this.placeHolder = placeHolder;
        this.errorImage = errorImage;

        spannedParser = new Html2SpannedParser(null);
        targets = new SoftReference<>(new HashSet<Target>());
        gifDrawables = new HashSet<>();
        noImage = false;
    }
    
    public static HtmlText from(String richText) {
        return fromHtml(richText);
    }
    
    public static HtmlText fromHtml(String richText) {
    	HtmlText r = new HtmlText();
        r.richText = richText;
        return r;
    }
    
    public HtmlText async(boolean async) {
        this.async = async;
        return this;
    }

    public HtmlText autoFix(boolean autoFix) {
        this.autoFix = autoFix;
        return this;
    }

    public HtmlText fix(OnImageFixCallback callback) {
        this.mImageFixCallback = callback;
        return this;
    }

    public HtmlText noImage(boolean noImage) {
        this.noImage = noImage;
        return this;
    }

    public HtmlText imageClick(OnImageClickListener imageClickListener) {
        this.onImageClickListener = imageClickListener;
        return this;
    }

    public HtmlText urlClick(OnURLClickListener onURLClickListener) {
        this.onURLClickListener = onURLClickListener;
        return this;
    }

    public HtmlText imageLongClick(OnImageLongClickListener imageLongClickListener) {
        this.onImageLongClickListener = imageLongClickListener;
        return this;
    }

    public HtmlText urlLongClick(OnUrlLongClickListener urlLongClickListener) {
        this.onUrlLongClickListener = urlLongClickListener;
        return this;
    }

    public HtmlText placeHolder(Drawable placeHolder) {
        this.placeHolder = placeHolder;
        return this;
    }

    public HtmlText error(Drawable errorImage) {
        this.errorImage = errorImage;
        return this;
    }

    public HtmlText placeHolder(@DrawableRes int placeHolder) {
        this.placeHolderRes = placeHolder;
        return this;
    }

    public HtmlText error(@DrawableRes int errorImage) {
        this.errorImageRes = errorImage;
        return this;
    }
    
    public void into(TextView textView) {
        this.textView = textView;
        textView.post(new Runnable() {
            @Override
            public void run() {
                if (async) {
                    setRichTextInTextViewAsync();
                } else {
                    HtmlText.this.textView.setText(generateRichText(richText));
                }
            }
        });
    }
    
    private void setPlaceHolder(GenericRequestBuilder load) {
        if (placeHolderRes > 0) {
            load.placeholder(placeHolderRes);
        } else {
            load.placeholder(placeHolder);
        }
    }

    private void setErrorImage(GenericRequestBuilder load) {
        if (errorImageRes > 0) {
            load.error(errorImageRes);
        } else {
            load.error(errorImage);
        }
    }
    
    private void setRichTextInTextViewAsync() {
        new AsyncTask<String, Void, Spanned>() {
            @Override
            protected Spanned doInBackground(String... params) {
                return generateRichText(params[0]);
            }

            @Override
            protected void onPostExecute(Spanned spanned) {
                super.onPostExecute(spanned);
                textView.setText(spanned);
            }
        }.execute(richText);
    }
    
    private Spanned generateRichText(String text) {
        recycle();
        matchImages(text);

        Spanned spanned = spannedParser.parse(text, asyncImageGetter);
        SpannableStringBuilder spannableStringBuilder;
        if (spanned instanceof SpannableStringBuilder) {
            spannableStringBuilder = (SpannableStringBuilder) spanned;
        } else {
            spannableStringBuilder = new SpannableStringBuilder(spanned);
        }

        // 处理图片得点击事件
        ImageSpan[] imageSpans = spannableStringBuilder.getSpans(0, spannableStringBuilder.length(), ImageSpan.class);
        final List<String> imageUrls = new ArrayList<>();
//
        for (int i = 0, size = imageSpans.length; i < size; i++) {
            ImageSpan imageSpan = imageSpans[i];
            String imageUrl = imageSpan.getSource();
            int start = spannableStringBuilder.getSpanStart(imageSpan);
            int end = spannableStringBuilder.getSpanEnd(imageSpan);
            imageUrls.add(imageUrl);

            final int finalI = i;
            ClickableSpan clickableSpan = new LongClickableSpan() {
                @Override
                public void onClick(View widget) {
                    if (onImageClickListener != null) {
                        onImageClickListener.imageClicked(imageUrls, finalI);
                    }
                }

                @Override
                public boolean onLongClick(View widget) {
                    return onImageLongClickListener != null && onImageLongClickListener.imageLongClicked(imageUrls, finalI);
                }
            };

            ClickableSpan[] clickableSpans = spannableStringBuilder.getSpans(start, end, ClickableSpan.class);
            if (clickableSpans != null && clickableSpans.length != 0) {
                for (ClickableSpan cs : clickableSpans) {
                    spannableStringBuilder.removeSpan(cs);
                }
            }
            spannableStringBuilder.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
//
//        // 处理超链接点击事件
//        URLSpan[] urlSpans = spannableStringBuilder.getSpans(0, spannableStringBuilder.length(), URLSpan.class);
//
//        for (int i = 0, size = urlSpans == null ? 0 : urlSpans.length; i < size; i++) {
//            URLSpan urlSpan = urlSpans[i];
//
//            int start = spannableStringBuilder.getSpanStart(urlSpan);
//            int end = spannableStringBuilder.getSpanEnd(urlSpan);
//
//            spannableStringBuilder.removeSpan(urlSpan);
//            spannableStringBuilder.setSpan(new LongCallableURLSpan(urlSpan.getURL(), onURLClickListener, onUrlLongClickListener), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
//        }
        return spanned;
    }
    
    private void recycle() {
        targets.clear();
        for (GifDrawable gifDrawable : gifDrawables) {
            gifDrawable.setCallback(null);
            gifDrawable.recycle();
        }
        gifDrawables.clear();
    }
    
    /**
     * 从文本中拿到<img/>标签,并获取图片url和宽高
     */
    private void matchImages(String text) {
        mImages = new HashMap<>();
        ImageHolder holder;
        Matcher imageMatcher, srcMatcher, widthMatcher, heightMatcher;
        int position = 0;
        imageMatcher = IMAGE_TAG_PATTERN.matcher(text);
        while (imageMatcher.find()) {
            String image = imageMatcher.group().trim();
            srcMatcher = IMAGE_SRC_PATTERN.matcher(image);
            String src = null;
            if (srcMatcher.find()) {
                src = getTextBetweenQuotation(srcMatcher.group().trim().substring(4));
            }
            if (TextUtils.isEmpty(src)) {
                continue;
            }
            holder = new ImageHolder(src, position);
            if (isGif(src)) {
                holder.setImageType(ImageHolder.GIF);
            }
            widthMatcher = IMAGE_WIDTH_PATTERN.matcher(image);
            if (widthMatcher.find()) {
                holder.setWidth(parseStringToInteger(getTextBetweenQuotation(widthMatcher.group().trim().substring(6))));
            }

            heightMatcher = IMAGE_HEIGHT_PATTERN.matcher(image);
            if (heightMatcher.find()) {
                holder.setHeight(parseStringToInteger(getTextBetweenQuotation(heightMatcher.group().trim().substring(6))));
            }

            mImages.put(holder.getSrc(), holder);
            position++;
        }
    }
    
    private static int parseStringToInteger(String integerStr) {
        int result = -1;
        if (!TextUtils.isEmpty(integerStr)) {
            try {
                result = Integer.parseInt(integerStr);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }
    
    /**
     * 从双引号之间取出字符串
     */
    @Nullable
    private static String getTextBetweenQuotation(String text) {
        Pattern pattern = Pattern.compile("\"(.*?)\"");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    private static boolean isGif(String path) {
        int index = path.lastIndexOf('.');
        return index > 0 && "gif".toUpperCase().equals(path.substring(index + 1).toUpperCase());
    }
    
    private int getRealWidth() {
        return textView.getWidth() - textView.getPaddingRight() - textView.getPaddingLeft();
    }
    
    private final Html.ImageGetter asyncImageGetter = new Html.ImageGetter() {
        @Override
        public Drawable getDrawable(String source) {
            if (noImage) {
                return new ColorDrawable(Color.TRANSPARENT);
            }
            final URLDrawable urlDrawable = new URLDrawable();
            final ImageHolder holder = mImages.get(source);
            final Target target;
            final GenericRequestBuilder load;
            if (!autoFix && mImageFixCallback != null && holder != null) {
                mImageFixCallback.onFix(holder, false);
                if (!holder.isShow()) {
                    return new ColorDrawable(Color.TRANSPARENT);
                }
            }
            if (holder != null && holder.isGif()) {
                target = new ImageTargetGif(urlDrawable, holder);
                load = Glide.with(textView.getContext()).load(source).asGif();
            } else {
                target = new ImageTargetBitmap(urlDrawable, holder);
                load = Glide.with(textView.getContext()).load(source).asBitmap();
            }
            if(targets.get()==null){
                targets= new SoftReference<>(new HashSet<Target>());
            }
            targets.get().add(target);
//            targets.add(target);
            if (!autoFix && mImageFixCallback != null && holder != null) {
                if (holder.getWidth() > 0 && holder.getHeight() > 0) {
                    load.override(holder.getWidth(), holder.getHeight());
                    if (holder.getScaleType() == ImageHolder.CENTER_CROP) {
                        if (holder.isGif()) {
                            ((GifTypeRequest) load).centerCrop();
                        } else {
                            ((BitmapTypeRequest) load).centerCrop();
                        }
                    } else if (holder.getScaleType() == ImageHolder.FIT_CENTER) {
                        if (holder.isGif()) {
                            ((GifTypeRequest) load).fitCenter();
                        } else {
                            ((BitmapTypeRequest) load).fitCenter();
                        }
                    }
                }
            }
            textView.post(new Runnable() {
                @Override
                public void run() {
                    setPlaceHolder(load);
                    setErrorImage(load);
                    load.into(target);
                }
            });
            return urlDrawable;
        }
    };
    
    private class ImageTargetGif extends SimpleTarget<GifDrawable> {
        private final URLDrawable urlDrawable;
        private final ImageHolder holder;

        private ImageTargetGif(URLDrawable urlDrawable, ImageHolder holder) {
            this.urlDrawable = urlDrawable;
            this.holder = holder;
        }

        @Override
        public void onResourceReady(GifDrawable resource, GlideAnimation<? super GifDrawable> glideAnimation) {
            Bitmap first = resource.getFirstFrame();
            if (!autoFix && (holder.getWidth() <= 0 || holder.getHeight() <= 0) && mImageFixCallback != null) {
                holder.setWidth(first.getWidth());
                holder.setHeight(first.getHeight());
                mImageFixCallback.onFix(holder, true);
            }
            if (autoFix || holder.isAutoFix()) {
                int width = getRealWidth();
                int height = (int) ((float) first.getHeight() * width / first.getWidth());
                urlDrawable.setBounds(0, 0, width, height);
                resource.setBounds(0, 0, width, height);
            } else {
                resource.setBounds(0, 0, holder.getWidth(), holder.getHeight());
                urlDrawable.setBounds(0, 0, holder.getWidth(), holder.getHeight());
            }
            urlDrawable.setDrawable(resource);
            gifDrawables.add(resource);
            if (holder.isAutoPlay()) {
                resource.setCallback(HtmlText.this);
                resource.start();
                resource.setLoopCount(GlideDrawable.LOOP_FOREVER);
                if (holder.isAutoStop() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
                    textView.addOnAttachStateChangeListener(HtmlText.this);
                }
            }
            textView.setText(textView.getText());
        }

        @Override
        public void onLoadStarted(Drawable placeholder) {
            super.onLoadStarted(placeholder);
            int width;
            int height;
            if (holder != null && holder.getHeight() > 0 && holder.getWidth() > 0) {
                width = holder.getWidth();
                height = holder.getHeight();
            } else {
                width = getRealWidth();
                height = placeholder.getBounds().height();
                if (height == 0) {
                    height = width / 2;
                }
            }
            placeholder.setBounds(0, 0, width, height);
            urlDrawable.setBounds(0, 0, width, height);
            urlDrawable.setDrawable(placeholder);
            textView.setText(textView.getText());
        }

        @Override
        public void onLoadFailed(Exception e, Drawable errorDrawable) {
            super.onLoadFailed(e, errorDrawable);
            int width;
            int height;
            if (holder != null && holder.getHeight() > 0 && holder.getWidth() > 0) {
                width = holder.getWidth();
                height = holder.getHeight();
            } else {
                width = getRealWidth();
                height = errorDrawable.getBounds().height();
                if (height == 0) {
                    height = width / 2;
                }
            }
            errorDrawable.setBounds(0, 0, width, height);
            urlDrawable.setBounds(0, 0, width, height);
            urlDrawable.setDrawable(errorDrawable);
            textView.setText(textView.getText());
        }
    }
    
    private class ImageTargetBitmap extends SimpleTarget<Bitmap> {
        private final URLDrawable urlDrawable;
        private final ImageHolder holder;

        public ImageTargetBitmap(URLDrawable urlDrawable, ImageHolder holder) {
            this.urlDrawable = urlDrawable;
            this.holder = holder;
        }

        @Override
        public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
            Drawable drawable = new BitmapDrawable(textView.getContext().getResources(), resource);
            if (!autoFix && (holder.getWidth() <= 0 || holder.getHeight() <= 0) && mImageFixCallback != null) {
                holder.setWidth(resource.getWidth());
                holder.setHeight(resource.getHeight());
                mImageFixCallback.onFix(holder, true);
            }
            if (autoFix || holder.isAutoFix()) {
                int width = getRealWidth();
                int height = (int) ((float) resource.getHeight() * width / resource.getWidth());
                urlDrawable.setBounds(0, 0, width, height);
                drawable.setBounds(0, 0, width, height);
            } else {
                drawable.setBounds(0, 0, holder.getWidth(), holder.getHeight());
                urlDrawable.setBounds(0, 0, holder.getWidth(), holder.getHeight());
            }
            urlDrawable.setDrawable(drawable);
            textView.setText(textView.getText());
        }

        @Override
        public void onLoadStarted(Drawable placeholder) {
            super.onLoadStarted(placeholder);
            int width;
            int height;
            if (holder != null && holder.getHeight() > 0 && holder.getWidth() > 0) {
                width = holder.getWidth();
                height = holder.getHeight();
            } else {
                width = getRealWidth();
                height = placeholder.getBounds().height();
                if (height == 0) {
                    height = width / 2;
                }
            }
            placeholder.setBounds(0, 0, width, height);
            urlDrawable.setBounds(0, 0, width, height);
            urlDrawable.setDrawable(placeholder);
            textView.setText(textView.getText());
        }

        @Override
        public void onLoadFailed(Exception e, Drawable errorDrawable) {
            super.onLoadFailed(e, errorDrawable);
            int width;
            int height;
            if (holder != null && holder.getHeight() > 0 && holder.getWidth() > 0) {
                width = holder.getWidth();
                height = holder.getHeight();
            } else {
                width = getRealWidth();
                height = errorDrawable.getBounds().height();
                if (height == 0) {
                    height = width / 2;
                }
            }
            errorDrawable.setBounds(0, 0, width, height);
            urlDrawable.setBounds(0, 0, width, height);
            urlDrawable.setDrawable(errorDrawable);
            textView.setText(textView.getText());
        }
    }
    
	@Override
	public void invalidateDrawable(Drawable who) {
		if (textView != null) {
            textView.invalidate();
        } else {
            recycle();
        }
	}

	@Override
	public void scheduleDrawable(Drawable who, Runnable what, long when) {
		
	}

	@Override
	public void unscheduleDrawable(Drawable who, Runnable what) {
		
	}

	
	
	@Override
	public void onViewAttachedToWindow(View v) {
		
	}

	@Override
	public void onViewDetachedFromWindow(View v) {
		recycle();
	}

}
