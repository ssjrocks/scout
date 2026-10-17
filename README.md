# Scout

A Google Discover-style news feed for Android, with the blocking controls Discover doesn't have.
Everything runs on the phone: no server, no account. Built 2026-10-17.

## What's in it

- **Feed**: Google-style header (weather, Google logo, search pill with voice and Lens), big picture
  cards, compact cards, YouTube cards with view counts, Reddit/HN cards with comment links, OzBargain
  deal cards with price/votes/expiry, and "Full coverage" grouping of the same story from several
  sources. Chips for For you / Latest / Videos / Unread and each followed topic. Pull to refresh,
  "N new stories" button, swipe right to save and left to hide, long-press or ⋮ for the menu.
- **Blocking** (Filters tab and the ⋮ menu): X/Twitter posts (on by default), stories about X posts,
  other social media, YouTube Shorts, all videos, paywalled sites, clickbait, deal articles,
  picture-less stories, whole source types (Reddit, YouTube, Google News…), any site, any source,
  any topic, muted words (whole words, or `re:` regex), per-source "only/skip words" filters.
- **Interests** (Following tab): 23 topics and ~110 sources picked from the owner's browsing
  history, each with on/off, Less/Normal/More, per-source weight and status. Add sources by pasting
  a site, feed URL, `r/sub`, YouTube `@channel`, GitHub repo, Steam game link, or keywords
  (Google News search). Links shared to Scout from other apps open the Add source dialog.
- **Learning**: opening, liking, saving and hiding adjust topic and source weights; "Why you're
  seeing this" shows the reasons. Can be turned off or reset.
- **Reading**: in-app browser (Custom Tabs), reader view with text size, serif option and read
  aloud; saved stories are kept offline in reader form. Saved and History tabs.
- **Search**: Google autocomplete, trending searches in Australia, recent searches, matches from
  your feed; results open in the Google app or the in-app browser (Google, DuckDuckGo, Startpage
  or Bing).
- **Weather**: Open-Meteo (no key), Park Ridge by default; hourly and 7-day sheet.
- **Background**: hourly refresh (configurable, Wi-Fi-only option), keyword alert notifications,
  home-screen widget (search bar + top stories), app shortcut to search.
- **Settings**: light/dark/black/system theme, wallpaper colours, text size, images
  always/Wi-Fi/never, story retention, settings backup/restore (JSON), OPML import/export.

## Source quirks (measured Oct 2026)

- **Reddit** allows only a few anonymous RSS requests a minute per IP, so without an API ID each
  refresh updates the 5 stalest subreddits. Settings → Reddit app ID switches to the official API
  (100 requests/minute, scores and comment counts): create an "installed app" at
  reddit.com/prefs/apps and paste its client ID.
- **YouTube's** feed server fails about half its requests in bad spells; Scout retries with
  back-off and tries failed channels again at the end of each refresh.
- Google News links are decoded to the publisher's URL so site blocking and pictures work.

## Building

Needs the Android SDK (`local.properties` → `sdk.dir`) and JDK 17+. On this PC, run builds through
`heavy` (see `~/.claude/CLAUDE.md`):

```bash
heavy -m 8G -- ./gradlew assembleRelease
```

The APK lands in `app/build/outputs/apk/release/app-release.apk`. Release builds are signed with
`~/.android/scout-release.jks`; the passwords are read from `keystore.properties` here or
`~/.android/scout-keystore.properties`. Keep that key: updates only install over the phone's copy
when they're signed with it.

The Google Sans Flex font (`app/src/main/res/font`) is under the SIL Open Font License
(`FONT-LICENSE-OFL.txt`).
