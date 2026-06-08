# Libraries (Admin Settings)
URL: https://wiki.kavitareader.com/guides/admin-settings/libraries/

The libraries tab is how you make your own content available to your users.

## Managing Libraries
The name of the library is highlighted in green. Clicking on it will send you to the series view inside the library.

## Important Fields

### Type
Type is the powerhouse of a Library. It will determine how the Scanner and UI are customized. Selecting the correct library will make an important difference in how Kavita works for you.

- **Book**: General-purpose library type.
- **Comic**: Designed with strict metadata adherence to Comic Vine in mind. Series name: `Series Name (Volume number)`. Does not render Volume tab. Supports Kavita+ features: Metadata.
- **Comic (Flexible)**: Earlier version of Comics, more flexible. Volume, TPBs, and Special tab all supported. Supports Kavita+ features: Metadata.
- **Image**: Custom built for Loose Image libraries. Only supports image files. Does not support Specials. Supports Kavita+ features: Metadata, Scrobbling.
- **Light Novels**: Japanese books using Volume nomenclature. Essentially Book with extra logic for Volumes. Supports Kavita+ features: Metadata, Scrobbling, Reviews, Recommendations.
- **Manga**: Supports manga/webtoon material with volume/chapter groupings. Supports Kavita+ features: Metadata, Scrobbling, Reviews, Recommendations. Supports Specials and Volumes.

## Adding Libraries
To add a library click on the "Add Library" button.

### General
Fill out the name and select the type from the dropdown.

### Folders
Pick the folders you want added using the folder picker. A library can contain more than 1 folder.

### Library Cover
Upload a small cover image (32x32 recommended).

### Advanced Settings

- **File types**: Controls what kind of files the scanner will look for.
- **Exclude Patterns**: Glob syntax to ignore scanning/processing of files and folders.
- **Manage Collections**: Create Collections from SeriesGroup tags in ComicInfo.xml or epub metadata.
- **Manage ReadingLists**: Create Reading Lists from StoryArc/StoryArcNumber and AlternativeSeries/AlternativeCount tags.
- **Allow Scrobbling** (requires Kavita+): Controls if users can scrobble series in this library.
- **Folder Watching**: Enabled by default, watches folders for modifications. Takes 10 minutes to trigger. Note: Docker on Windows over WSL2 does not support folder watching.
- **Include in Dashboard**: Should series be included on the Dashboard?
- **Include in Search**: Should series and derived info be included in search results?
