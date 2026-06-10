import zipfile, os

epub_path = "C:/Users/luned/Documents/projects/mimiral/test_book.epub"

with zipfile.ZipFile(epub_path, "w", zipfile.ZIP_DEFLATED) as zf:
    mimetype_info = zipfile.ZipInfo("mimetype")
    mimetype_info.compress_type = zipfile.ZIP_STORED
    zf.writestr(mimetype_info, "application/epub+zip")

    zf.writestr(
        "META-INF/container.xml",
        """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>""",
    )

    zf.writestr(
        "OEBPS/content.opf",
        """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>Test Book</dc:title>
    <dc:creator>Test Author</dc:creator>
    <dc:description>A test EPUB for debugging</dc:description>
    <dc:identifier id="uid">test-001</dc:identifier>
    <dc:language>en</dc:language>
  </metadata>
  <manifest>
    <item id="toc" href="toc.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="chapter1" href="text/chapter1.xhtml" media-type="application/xhtml+xml"/>
    <item id="chapter2" href="text/chapter2.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine>
    <itemref idref="chapter1"/>
    <itemref idref="chapter2"/>
  </spine>
</package>""",
    )

    zf.writestr(
        "OEBPS/toc.xhtml",
        """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><title>Table of Contents</title></head>
<body>
  <nav epub:type="toc">
    <h1>Contents</h1>
    <ol>
      <li><a href="text/chapter1.xhtml">Chapter One</a></li>
      <li><a href="text/chapter2.xhtml">Chapter Two</a></li>
    </ol>
  </nav>
</body>
</html>""",
    )

    zf.writestr(
        "OEBPS/text/chapter1.xhtml",
        """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><title>Chapter One</title></head>
<body>
  <h1>Chapter One: The Beginning</h1>
  <p>This is the first chapter of the test book. It contains some sample text to verify that the EPUB parser is working correctly.</p>
  <p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.</p>
  <p>Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.</p>
</body>
</html>""",
    )

    zf.writestr(
        "OEBPS/text/chapter2.xhtml",
        """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><title>Chapter Two</title></head>
<body>
  <h1>Chapter Two: The End</h1>
  <p>This is the second and final chapter of the test book.</p>
  <p>Sunt in culpa qui officia deserunt mollit anim id est laborum. Sed ut perspiciatis unde omnis iste natus error.</p>
  <p>Nemo enim ipsam voluptatem quia voluptas sit aspernatur aut odit aut fugit.</p>
</body>
</html>""",
    )

print(f"Created: {epub_path} ({os.path.getsize(epub_path)} bytes)")
