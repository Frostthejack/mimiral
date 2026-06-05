package com.mimiral.app.data.repository

// LibraryRepository requires FileScanner which has Android framework dependencies
// (Context, MediaStore) that are not available in unit tests without Robolectric.
// These tests are deferred to the androidTest suite or integration tests.
