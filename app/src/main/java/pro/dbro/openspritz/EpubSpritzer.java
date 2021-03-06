package pro.dbro.openspritz;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.Html;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;

import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.epub.EpubReader;
import pro.dbro.openspritz.events.NextChapterEvent;

/**
 * Parse an .epub into a Queue of words
 * and display them on a TextView at
 * a given WPM
 */
// TODO: Save epub title : chapter-word
// TODO: Save State for multiple books
public class EpubSpritzer extends Spritzer {

    private static final String PREFS = "espritz";

    private Uri mEpubUri;
    private Book mBook;
    private int mChapter;
    private int mMaxChapter;

    public EpubSpritzer(TextView target) {
        super(target);
        restoreState(true);
    }

    public EpubSpritzer(TextView target, Uri epubPath) {
        super(target);
        mChapter = 0;

        openEpub(epubPath);
        mTarget.setText(mTarget.getContext().getString(R.string.touch_to_start));
    }

    public void setEpubPath(Uri epubPath) {
        pause();
        openEpub(epubPath);
        mTarget.setText(mTarget.getContext().getString(R.string.touch_to_start));
    }

    public void openEpub(Uri epubUri) {
        try {
            InputStream epubInputStream = mTarget.getContext().getContentResolver().openInputStream(epubUri);
            String epubPath = FileUtils.getPath(mTarget.getContext(), epubUri);
            // Opening an attachment in Gmail may produce
            // content://gmail-ls/xxx@xxx.com/messages/9852/attachments/0.1/BEST/false
            // and no path
            if (epubPath != null && !epubPath.contains("epub")) {
                reportFileUnsupported();
                return;
            }
            mEpubUri = epubUri;
            mBook = (new EpubReader()).readEpub(epubInputStream);
            mMaxChapter = mBook.getSpine().getSpineReferences().size();
            restoreState(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Book getBook() {
        return mBook;
    }

    public void printChapter(int chapter) {
        mChapter = chapter;
        setText(loadCleanStringFromChapter(mChapter));
        saveState();
    }

    public int getCurrentChapter() {
        return mChapter;
    }

    public int getMaxChapter() {
        return mMaxChapter;
    }

    public boolean bookSelected() {
        return mBook != null;
    }

    protected void processNextWord() throws InterruptedException {
        super.processNextWord();
        if (mPlaying && mPlayingRequested && mWordQueue.isEmpty() && (mChapter < mMaxChapter)) {
            printNextChapter();
            if (mBus != null) {
                mBus.post(new NextChapterEvent(mChapter));
            }
        }
    }

    private void printNextChapter() {
        setText(loadCleanStringFromChapter(mChapter++));
        saveState();
        if (VERBOSE) Log.i(TAG, "starting next chapter: " + mChapter);
    }

    private String loadCleanStringFromChapter(int chapter) {
        try {
            String bookStr = new String(mBook.getSpine().getResource(chapter).getData(), "UTF-8");
            return Html.fromHtml(bookStr).toString().replace("\n", "").replaceAll("(?s)<!--.*?-->", "");
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Parsing failed " + e.getMessage());
            return "";
        }
    }

    public void saveState() {
        if (mBook != null) {
            Log.i(TAG, "Saving state");
            SharedPreferences.Editor editor = mTarget.getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
            editor.putInt("Chapter", mChapter)
                    .putString("epubUri", mEpubUri.toString())
                    .putInt("Word", mWordArray.length - mWordQueue.size())
                    .putString("Title", mBook.getTitle())
                    .putInt("Wpm", mWPM)
                    .apply();
        }
    }

    private void restoreState(boolean openLastEpubUri) {
        SharedPreferences prefs = mTarget.getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (openLastEpubUri) {
            if (prefs.contains("epubUri")) {
                openEpub(Uri.parse(prefs.getString("epubUri", "")));
            }
        } else if (mBook.getTitle().compareTo(prefs.getString("Title", "<>?l")) == 0) {
            mChapter = prefs.getInt("Chapter", 0);
            setText(loadCleanStringFromChapter(mChapter));
            int oldSize = prefs.getInt("Word", 0);
            setWpm(prefs.getInt("Wpm", 500));
            while (mWordQueue.size() > oldSize) {
                mWordQueue.remove();
            }
        } else {
            mChapter = 0;
            setText(loadCleanStringFromChapter(mChapter));
        }
        if (!mPlaying) {
            mTarget.setText(mTarget.getContext().getString(R.string.touch_to_start));
        }
    }

    private void reportFileUnsupported() {
        Toast.makeText(mTarget.getContext(), mTarget.getContext().getString(R.string.unsupported_file), Toast.LENGTH_LONG).show();
    }

}
