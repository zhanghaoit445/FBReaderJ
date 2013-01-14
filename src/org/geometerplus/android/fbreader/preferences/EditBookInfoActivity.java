/*
 * Copyright (C) 2009-2013 Geometer Plus <contact@geometerplus.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.android.fbreader.preferences;

import java.util.*;

import android.content.Context;
import android.content.Intent;

import org.geometerplus.zlibrary.core.filesystem.ZLFile;
import org.geometerplus.zlibrary.core.encodings.Encoding;
import org.geometerplus.zlibrary.core.language.ZLLanguageUtil;
import org.geometerplus.zlibrary.core.resources.ZLResource;

import org.geometerplus.zlibrary.text.hyphenation.ZLTextHyphenator;

import org.geometerplus.fbreader.book.*;
import org.geometerplus.fbreader.bookmodel.BookReadingException;
import org.geometerplus.fbreader.formats.*;
import org.geometerplus.fbreader.library.*;

import org.geometerplus.android.fbreader.FBReader;
import org.geometerplus.android.fbreader.library.BookInfoActivity;
import org.geometerplus.android.fbreader.libraryService.SQLiteBooksDatabase;
import org.geometerplus.android.fbreader.preferences.activityprefs.*;

class BookTitlePreference extends ZLStringPreference {
	private final Book myBook;

	BookTitlePreference(Context context, ZLResource rootResource, String resourceKey, Book book) {
		super(context, rootResource, resourceKey);
		myBook = book;
		super.setValue(book.getTitle());
	}

	@Override
	protected void setValue(String value) {
		super.setValue(value);
		myBook.setTitle(value);
	}
}

class LanguagePreference extends ZLStringListPreference {
	private final Book myBook;

	LanguagePreference(Context context, ZLResource rootResource, String resourceKey, Book book) {
		super(context, rootResource, resourceKey);
		myBook = book;
		final TreeSet<String> set = new TreeSet<String>(new ZLLanguageUtil.CodeComparator());
		set.addAll(ZLTextHyphenator.Instance().languageCodes());
		set.add(ZLLanguageUtil.OTHER_LANGUAGE_CODE);

		final int size = set.size();
		String[] codes = new String[size];
		String[] names = new String[size];
		int index = 0;
		for (String code : set) {
			codes[index] = code;
			names[index] = ZLLanguageUtil.languageName(code);
			++index;
		}
		setLists(codes, names);
		final String language = myBook.getLanguage();
		if (language == null || !setInitialValue(language)) {
			setInitialValue(ZLLanguageUtil.OTHER_LANGUAGE_CODE);
		}
	}

	@Override
	protected void onDialogClosed(boolean result) {
		super.onDialogClosed(result);
		if (result) {
			final String value = getValue();
			myBook.setLanguage(value.length() > 0 ? value : null);
		}
	}
}

class EncodingPreference extends ZLStringListPreference {
	private final Book myBook;

	EncodingPreference(Context context, ZLResource rootResource, String resourceKey, Book book) {
		super(context, rootResource, resourceKey);
		myBook = book;

		final FormatPlugin plugin;
		try {
			plugin = book.getPlugin();
		} catch (BookReadingException e) {
			return;
		}

		final List<Encoding> encodings =
			new ArrayList<Encoding>(plugin.supportedEncodings().encodings());
		Collections.sort(encodings, new Comparator<Encoding>() {
			public int compare(Encoding e1, Encoding e2) {
				return e1.DisplayName.compareTo(e2.DisplayName);
			}
		});
		final String[] codes = new String[encodings.size()];
		final String[] names = new String[encodings.size()];
		int index = 0;
		for (Encoding e : encodings) {
			//addItem(e.Family, e.Name, e.DisplayName);
			codes[index] = e.Name;
			names[index] = e.DisplayName;
			++index;
		}
		setLists(codes, names);
		if (encodings.size() == 1) {
			setInitialValue(codes[0]);
			setEnabled(false);
		} else {
			final String bookEncoding = book.getEncoding();
			if (bookEncoding != null) {
				setInitialValue(bookEncoding.toLowerCase());
			}
		}
	}

	@Override
	protected void onDialogClosed(boolean result) {
		super.onDialogClosed(result);
		if (result) {
			final String value = getValue();
			if (!value.equalsIgnoreCase(myBook.getEncoding())) {
				myBook.setEncoding(value);
				((EditBookInfoActivity)getContext()).setResult(FBReader.RESULT_RELOAD_BOOK);
			}
		}
	}
}

public class EditBookInfoActivity extends ZLPreferenceActivity {
	private Book myBook;
	private final List<String> myAuthors = new ArrayList<String>();
	private final List<String> myTags = new ArrayList<String>();
	private ZLSpinnerActivityPreference myAuthorPref;
	private ZLSpinnerActivityPreference myTagPref;

	private final HashMap<Integer,ZLActivityPreference> myActivityPrefs =
		new HashMap<Integer,ZLActivityPreference>();

	public EditBookInfoActivity() {
		super("BookInfo");
	}

	private class AuthorsHolder implements ZLActivityPreference.ListHolder {
		public List<String> getValue() {
			return myBook.getAuthors();
		}

		public void setValue(List<String> l) {
			myBook.setAuthors(l);
		}
	}

	private class TagsHolder implements ZLActivityPreference.ListHolder {
		private List<String> myValues;

		public synchronized List<String> getValue() {
			if (myValues == null) {
				myValues = new LinkedList<String>();
				for (Tag t : myBook.tags()) {
					myValues.add(t.toString("/"));
				}
			}
			return myValues;
		}

		public synchronized void setValue(List<String> tags) {
			if (!tags.equals(myValues)) {
				myValues = null;
				myBook.removeAllTags();
				for (String t : tags) {
					myBook.addTag(Tag.getTag(t.split("/")));
				}
			}
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		ZLActivityPreference p = myActivityPrefs.get(requestCode);
		if (resultCode == RESULT_OK) {
			p.setValue(data);
		}
		myBook.save();
		myAuthors.clear();
		myTags.clear();
		for (Author a : BooksDatabase.Instance().loadAuthors()) {
			if (!myAuthors.contains(a.DisplayName))	myAuthors.add(a.DisplayName);//TODO: booksdb should clean itself
		}
		myAuthorPref.setSuggestions(myAuthors);
		for (Tag t : BooksDatabase.Instance().loadTags()) {
			if (!myTags.contains(t.Name)) myTags.add(t.Name);//TODO: booksdb should clean itself
		}
		myTagPref.setSuggestions(myTags);
	}

	@Override
	protected void init(Intent intent) {
		if (SQLiteBooksDatabase.Instance() == null) {
			new SQLiteBooksDatabase(this, "LIBRARY");
		}

		final String path = intent.getStringExtra(BookInfoActivity.CURRENT_BOOK_PATH_KEY);
		final ZLFile file = ZLFile.createFileByPath(path);
		myBook = Book.getByFile(file);
		setResult(FBReader.RESULT_REPAINT);

		if (myBook == null) {
			finish();
			return;
		}

		for (Author a : BooksDatabase.Instance().loadAuthors()) {
			if (!myAuthors.contains(a.DisplayName))	myAuthors.add(a.DisplayName);//TODO: booksdb should clean itself
		}
		List<Tag> tags = BooksDatabase.Instance().loadTags();
		if (tags == null) {
			tags = Collections.<Tag>emptyList();
		}
		for (Tag t : tags) {
			if (!myTags.contains(t.Name)) myTags.add(t.Name);//TODO: booksdb should clean itself
		}

		myAuthorPref = new ZLSpinnerActivityPreference(
			this, new AuthorsHolder(), myActivityPrefs, myAuthors,
			Resource, "authors"
		);

		myTagPref = new ZLSpinnerActivityPreference(
			this, new TagsHolder(), myActivityPrefs, myTags,
			Resource, "tags"
		);

		addPreference(new BookTitlePreference(this, Resource, "title", myBook));
		addPreference(myAuthorPref);
		addPreference(myTagPref);
		addPreference(new LanguagePreference(this, Resource, "language", myBook));
		addPreference(new EncodingPreference(this, Resource, "encoding", myBook));
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (myBook != null) {
			myBook.save();
		}
	}
}
