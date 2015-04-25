/*
 * Copyright (C) 2009-2015 FBReader.ORG Limited <contact@fbreader.org>
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

package org.geometerplus.android.fbreader.bookmark;

import java.util.*;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.content.*;

import yuku.ambilwarna.widget.AmbilWarnaPrefWidgetView;

import org.geometerplus.zlibrary.core.util.MiscUtil;
import org.geometerplus.zlibrary.core.resources.ZLResource;
import org.geometerplus.zlibrary.core.options.ZLStringOption;

import org.geometerplus.zlibrary.ui.android.R;

import org.geometerplus.fbreader.book.*;

import org.geometerplus.android.fbreader.FBReader;
import org.geometerplus.android.fbreader.OrientationUtil;
import org.geometerplus.android.fbreader.api.FBReaderIntents;
import org.geometerplus.android.fbreader.libraryService.BookCollectionShadow;
import org.geometerplus.android.util.*;

public class BookmarksActivity extends Activity {
	private static final int OPEN_ITEM_ID = 0;
	private static final int EDIT_ITEM_ID = 1;
	private static final int DELETE_ITEM_ID = 2;

	private TabHost myTabHost;

	private final Map<Integer,HighlightingStyle> myStyles =
		new HashMap<Integer,HighlightingStyle>();

	private final BookCollectionShadow myCollection = new BookCollectionShadow();
	private volatile Book myBook;

	private final Comparator<Bookmark> myComparator = new Bookmark.ByTimeComparator();

	private volatile BookmarksAdapter myThisBookAdapter;
	private volatile BookmarksAdapter myAllBooksAdapter;
	private volatile BookmarksAdapter mySearchResultsAdapter;

	private final ZLResource myResource = ZLResource.resource("bookmarksView");
	private final ZLStringOption myBookmarkSearchPatternOption =
		new ZLStringOption("BookmarkSearch", "Pattern", "");

	private void createTab(String tag, int id) {
		final String label = myResource.getResource(tag).getValue();
		myTabHost.addTab(myTabHost.newTabSpec(tag).setIndicator(label).setContent(id));
	}

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);

		Thread.setDefaultUncaughtExceptionHandler(new org.geometerplus.zlibrary.ui.android.library.UncaughtExceptionHandler(this));
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.bookmarks);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

		final SearchManager manager = (SearchManager)getSystemService(SEARCH_SERVICE);
		manager.setOnCancelListener(null);

        myTabHost = (TabHost)findViewById(R.id.bookmarks_tabhost);
		myTabHost.setup();

		createTab("thisBook", R.id.bookmarks_this_book);
		createTab("allBooks", R.id.bookmarks_all_books);
		createTab("search", R.id.bookmarks_search);

		myTabHost.setOnTabChangedListener(new TabHost.OnTabChangeListener() {
			public void onTabChanged(String tabId) {
				if ("search".equals(tabId)) {
					findViewById(R.id.bookmarks_search_results).setVisibility(View.GONE);
					onSearchRequested();
				}
			}
		});

		myBook = FBReaderIntents.getBookExtra(getIntent());
		if (myBook == null) {
			finish();
		}
	}

	private class Initializer implements Runnable {
		public void run() {
			for (HighlightingStyle style : myCollection.highlightingStyles()) {
				myStyles.put(style.Id, style);
			}

			for (BookmarkQuery query = new BookmarkQuery(myBook, 20); ; query = query.next()) {
				final List<Bookmark> thisBookBookmarks = myCollection.bookmarks(query);
				if (thisBookBookmarks.isEmpty()) {
					break;
				}
				myThisBookAdapter.addAll(thisBookBookmarks);
				myAllBooksAdapter.addAll(thisBookBookmarks);
			}
			for (BookmarkQuery query = new BookmarkQuery(20); ; query = query.next()) {
				final List<Bookmark> allBookmarks = myCollection.bookmarks(query);
				if (allBookmarks.isEmpty()) {
					break;
				}
				myAllBooksAdapter.addAll(allBookmarks);
			}
			runOnUiThread(new Runnable() {
				public void run() {
					setProgressBarIndeterminateVisibility(false);
				}
			});
		}
	}

	@Override
	protected void onStart() {
		super.onStart();

		runOnUiThread(new Runnable() {
			public void run() {
				setProgressBarIndeterminateVisibility(true);
			}
		});

		myCollection.bindToService(this, new Runnable() {
			public void run() {
				if (myAllBooksAdapter != null) {
					return;
				}

				myThisBookAdapter =
					new BookmarksAdapter((ListView)findViewById(R.id.bookmarks_this_book), true);
				myAllBooksAdapter =
					new BookmarksAdapter((ListView)findViewById(R.id.bookmarks_all_books), false);

				new Thread(new Initializer()).start();
			}
		});

		OrientationUtil.setOrientation(this, getIntent());
	}

	@Override
	protected void onNewIntent(Intent intent) {
		OrientationUtil.setOrientation(this, intent);

		if (!Intent.ACTION_SEARCH.equals(intent.getAction())) {
			return;
		}
		String pattern = intent.getStringExtra(SearchManager.QUERY);
		myBookmarkSearchPatternOption.setValue(pattern);

		final LinkedList<Bookmark> bookmarks = new LinkedList<Bookmark>();
		pattern = pattern.toLowerCase();
		for (Bookmark b : myAllBooksAdapter.bookmarks()) {
			if (MiscUtil.matchesIgnoreCase(b.getText(), pattern)) {
				bookmarks.add(b);
			}
		}
		if (!bookmarks.isEmpty()) {
			final ListView resultsView = (ListView)findViewById(R.id.bookmarks_search_results);
			resultsView.setVisibility(View.VISIBLE);
			if (mySearchResultsAdapter == null) {
				mySearchResultsAdapter = new BookmarksAdapter(resultsView, false);
			} else {
				mySearchResultsAdapter.clear();
			}
			mySearchResultsAdapter.addAll(bookmarks);
		} else {
			UIUtil.showErrorMessage(this, "bookmarkNotFound");
		}
	}

	@Override
	protected void onDestroy() {
		myCollection.unbind();
		super.onDestroy();
	}

	@Override
	public boolean onSearchRequested() {
		if (DeviceType.Instance().hasStandardSearchDialog()) {
			startSearch(myBookmarkSearchPatternOption.getValue(), true, null, false);
		} else {
			SearchDialogUtil.showDialog(this, BookmarksActivity.class, myBookmarkSearchPatternOption.getValue(), null);
		}
		return true;
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		final int position = ((AdapterView.AdapterContextMenuInfo)item.getMenuInfo()).position;
		final ListView view = (ListView)myTabHost.getCurrentView();
		final Bookmark bookmark = ((BookmarksAdapter)view.getAdapter()).getItem(position);
		switch (item.getItemId()) {
			case OPEN_ITEM_ID:
				gotoBookmark(bookmark);
				return true;
			case EDIT_ITEM_ID:
				final Intent intent = new Intent(this, EditBookmarkActivity.class);
				FBReaderIntents.putBookmarkExtra(intent, bookmark);
				OrientationUtil.startActivity(this, intent);
				// TODO: implement IBookCollection listener
				return true;
			case DELETE_ITEM_ID:
				myCollection.deleteBookmark(bookmark);
				if (myThisBookAdapter != null) {
					myThisBookAdapter.remove(bookmark);
				}
				if (myAllBooksAdapter != null) {
					myAllBooksAdapter.remove(bookmark);
				}
				if (mySearchResultsAdapter != null) {
					mySearchResultsAdapter.remove(bookmark);
				}
				return true;
		}
		return super.onContextItemSelected(item);
	}

	private void addBookmark() {
		final Bookmark bookmark = FBReaderIntents.getBookmarkExtra(getIntent());
		if (bookmark != null) {
			myCollection.saveBookmark(bookmark);
			myThisBookAdapter.add(bookmark);
			myAllBooksAdapter.add(bookmark);
		}
	}

	private void gotoBookmark(Bookmark bookmark) {
		bookmark.markAsAccessed();
		myCollection.saveBookmark(bookmark);
		final Book book = myCollection.getBookById(bookmark.BookId);
		if (book != null) {
			FBReader.openBookActivity(this, book, bookmark);
		} else {
			UIUtil.showErrorMessage(this, "cannotOpenBook");
		}
	}

	private final class BookmarksAdapter extends BaseAdapter implements AdapterView.OnItemClickListener, View.OnCreateContextMenuListener {
		private final List<Bookmark> myBookmarks =
			Collections.synchronizedList(new LinkedList<Bookmark>());
		// TODO: change to false on new bookmark creation
		private final boolean myShowAddBookmarkItem;

		BookmarksAdapter(ListView listView, boolean showAddBookmarkItem) {
			myShowAddBookmarkItem = showAddBookmarkItem;
			listView.setAdapter(this);
			listView.setOnItemClickListener(this);
			listView.setOnCreateContextMenuListener(this);
		}

		public List<Bookmark> bookmarks() {
			return Collections.unmodifiableList(myBookmarks);
		}

		public void addAll(final List<Bookmark> bookmarks) {
			runOnUiThread(new Runnable() {
				public void run() {
					synchronized (myBookmarks) {
						for (Bookmark b : bookmarks) {
							final int position = Collections.binarySearch(myBookmarks, b, myComparator);
							if (position < 0) {
								myBookmarks.add(- position - 1, b);
							}
						}
					}
					notifyDataSetChanged();
				}
			});
		}

		public void add(final Bookmark b) {
			runOnUiThread(new Runnable() {
				public void run() {
					synchronized (myBookmarks) {
						final int position = Collections.binarySearch(myBookmarks, b, myComparator);
						if (position < 0) {
							myBookmarks.add(- position - 1, b);
						}
					}
					notifyDataSetChanged();
				}
			});
		}

		public void remove(final Bookmark b) {
			runOnUiThread(new Runnable() {
				public void run() {
					myBookmarks.remove(b);
					notifyDataSetChanged();
				}
			});
		}

		public void clear() {
			runOnUiThread(new Runnable() {
				public void run() {
					myBookmarks.clear();
					notifyDataSetChanged();
				}
			});
		}

		public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {
			final int position = ((AdapterView.AdapterContextMenuInfo)menuInfo).position;
			if (getItem(position) != null) {
				menu.add(0, OPEN_ITEM_ID, 0, myResource.getResource("openBook").getValue());
				menu.add(0, EDIT_ITEM_ID, 0, myResource.getResource("editBookmark").getValue());
				menu.add(0, DELETE_ITEM_ID, 0, myResource.getResource("deleteBookmark").getValue());
			}
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			final View view = (convertView != null) ? convertView :
				LayoutInflater.from(parent.getContext()).inflate(R.layout.bookmark_item, parent, false);
			final ImageView imageView = ViewUtil.findImageView(view, R.id.bookmark_item_icon);
			final View colorContainer = ViewUtil.findView(view, R.id.bookmark_item_color_container);
			final AmbilWarnaPrefWidgetView colorView =
				(AmbilWarnaPrefWidgetView)ViewUtil.findView(view, R.id.bookmark_item_color);
			final TextView textView = ViewUtil.findTextView(view, R.id.bookmark_item_text);
			final TextView bookTitleView = ViewUtil.findTextView(view, R.id.bookmark_item_booktitle);

			final Bookmark bookmark = getItem(position);
			if (bookmark == null) {
				imageView.setVisibility(View.VISIBLE);
				imageView.setImageResource(R.drawable.ic_list_plus);
				colorContainer.setVisibility(View.GONE);
				textView.setText(myResource.getResource("new").getValue());
				bookTitleView.setVisibility(View.GONE);
			} else {
				imageView.setVisibility(View.GONE);
				colorContainer.setVisibility(View.VISIBLE);
				BookmarksUtil.setupColorView(colorView, myStyles.get(bookmark.getStyleId()));
				textView.setText(bookmark.getText());
				if (myShowAddBookmarkItem) {
					bookTitleView.setVisibility(View.GONE);
				} else {
					bookTitleView.setVisibility(View.VISIBLE);
					bookTitleView.setText(bookmark.BookTitle);
				}
			}
			return view;
		}

		@Override
		public final boolean areAllItemsEnabled() {
			return true;
		}

		@Override
		public final boolean isEnabled(int position) {
			return true;
		}

		@Override
		public final long getItemId(int position) {
			return position;
		}

		@Override
		public final Bookmark getItem(int position) {
			if (myShowAddBookmarkItem) {
				--position;
			}
			return (position >= 0) ? myBookmarks.get(position) : null;
		}

		@Override
		public final int getCount() {
			return myShowAddBookmarkItem ? myBookmarks.size() + 1 : myBookmarks.size();
		}

		public final void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			final Bookmark bookmark = getItem(position);
			if (bookmark != null) {
				gotoBookmark(bookmark);
			} else {
				addBookmark();
			}
		}
	}
}
