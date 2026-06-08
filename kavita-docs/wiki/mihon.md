# Mihon (Tachiyomi-like)
URL: https://wiki.kavitareader.com/guides/3rdparty/tachi-like/

> ⚠️ Currently, we only support the official Mihon app and Komikku.
> Forks that are confirmed to work: TachiyomiSY.

## Add Kavita extension repository
You can click on this button from your Android to automatically add the repo:

Otherwise:
1. In Mihon, go to "More" > "Settings" > "Browse" > "Extension repos"
2. Click on "Add"
3. Input this URL: `https://raw.githubusercontent.com/Kareadita/tach-extension/repo/index.min.json`

## Installation
1. First of all make sure Kavita is updated.
2. After that, after opening Mihon, go to Browse in the lower nav.
3. Select the Extensions tab at the top of the screen.
4. Now you can either scroll down until you find it or hit the search icon and write Kavita in it. (Make sure to toggle "Multi" in the languages to see the extension)
5. Once you find or search tap install.
6. In the confirmation menu hit install again.
7. Make sure to click "Trust" on the extension once installed.

## Setup
To make the extension link with your Kavita instance we need the OPDS URL. The steps to get the OPDS URL are:
1. Access your Kavita instance from your web browser and log in
2. Access your Kavita user dashboard
3. Switch to "3rd Party Clients" tab
4. Copy the text under the OPDS URL
5. Once the OPDS URL is obtained open Mihon
6. Go back to the Mihon extensions tab
7. Tap Kavita
8. Tap the gear icon in one of the sources
9. Tap OPDS URL setting
10. Paste your OPDS URL (If your OPDS URL address differs from your Kavita address, change it.)
11. Tap ok
12. Restart Mihon
13. Browse your library

## Customization
Once installed the user has access to 3 sources. This means you can have access to 3 different Kavita servers.
On each source, you can set up different OPDS URLs and different filter preferences. By default, these are differentiated with a number. 1, 2 and 3. You can change each identifier with your own name.

> ⚠️ You can only add different domains as instances—adding the same domain twice will cause both sources to break.

### Advanced customization
- **Tags Grouping**: Only for forks. Turns genres/tags list into organized categories.
- **Dynamic Cover Updates**: Every time your title is refreshed, it will check what Volume/Issue you're currently reading and sync the cover.
- **EPUB Visibility**: Disabled by default because Kavita Epubs can't be read in Mihon, but you can enable it.
- **Reading List: Display Release Date**: When enabled, displays original Release Date instead of date added.
- **Chapters: Display Release Dates**: When enabled, displays original Release Date for individual chapters.
- **Chapter Title Format**: Customizable using variables like $Type, $No, $Title, $CleanTitle, $Pages, $Size, $Volume, $SeriesName, $LibraryName, $Format, $Created, $ReleaseDate
- **Scanlator Format**: Similar to chapter title, customizable with same metadata variables.
- **Allowed Libraries on Latest/Popular Feeds & Suggestions**: Filter content from selected Kavita libraries.
- **Allowed Libraries in Search**: Filter content when performing global search.

### Sync Progress with Kavita
Kavita has progress sync with Mihon. It will automatically mark completed chapters as read in both apps. It does not sync page progress, it only syncs when a chapter is complete.

To activate tracking go to settings > Tracking and click on Kavita at the bottom.

After activating it will appear with the rest of the trackers on your manga page. Make sure it shows it is active otherwise, it will not sync.
