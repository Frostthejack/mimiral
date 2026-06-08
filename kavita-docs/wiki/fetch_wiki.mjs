// Node.js script to fetch Kavita wiki pages and convert to markdown
const fs = require('fs');
const path = require('path');

const OUTPUT_DIR = 'C:\\Users\\luned\\Documents\\Projects\\Mimiral\\kavita-docs\\wiki';

const PAGES = [
    ["getting-started", "https://wiki.kavitareader.com/getting-started/"],
    ["installation-native", "https://wiki.kavitareader.com/installation/native/"],
    ["installation-docker", "https://wiki.kavitareader.com/installation/docker/"],
    ["installation-docker-lsio", "https://wiki.kavitareader.com/installation/docker/lsio/"],
    ["installation-docker-dockerhub", "https://wiki.kavitareader.com/installation/docker/dockerhub/"],
    ["installation-docker-github", "https://wiki.kavitareader.com/installation/docker/github/"],
    ["installation-nas", "https://wiki.kavitareader.com/installation/nas/"],
    ["installation-managed-hosting", "https://wiki.kavitareader.com/installation/managed-hosting/"],
    ["installation-remote-access", "https://wiki.kavitareader.com/installation/remote-access/"],
    ["installation-remote-access-apache2", "https://wiki.kavitareader.com/installation/remote-access/apache2-example/"],
    ["installation-remote-access-caddy", "https://wiki.kavitareader.com/installation/remote-access/caddy-example/"],
    ["installation-remote-access-haproxy", "https://wiki.kavitareader.com/installation/remote-access/haproxy-example/"],
    ["installation-remote-access-nginx", "https://wiki.kavitareader.com/installation/remote-access/nginx-example/"],
    ["installation-remote-access-npm", "https://wiki.kavitareader.com/installation/remote-access/npm-example/"],
    ["installation-remote-access-swag", "https://wiki.kavitareader.com/installation/remote-access/swag-example/"],
    ["installation-updating", "https://wiki.kavitareader.com/installation/updating/"],
    ["installation-updating-docker", "https://wiki.kavitareader.com/installation/updating/updating-docker/"],
    ["installation-updating-native", "https://wiki.kavitareader.com/installation/updating/updating-native/"],
    ["guides", "https://wiki.kavitareader.com/guides/"],
    ["guides-admin-general", "https://wiki.kavitareader.com/guides/admin-settings/general/"],
    ["guides-admin-media", "https://wiki.kavitareader.com/guides/admin-settings/media/"],
    ["guides-admin-email", "https://wiki.kavitareader.com/guides/admin-settings/email/"],
    ["guides-admin-users", "https://wiki.kavitareader.com/guides/admin-settings/users/"],
    ["guides-admin-openid-connect", "https://wiki.kavitareader.com/guides/admin-settings/open-id-connect/"],
    ["guides-admin-libraries", "https://wiki.kavitareader.com/guides/admin-settings/libraries/"],
    ["guides-admin-tasks", "https://wiki.kavitareader.com/guides/admin-settings/tasks/"],
    ["guides-admin-system", "https://wiki.kavitareader.com/guides/admin-settings/system/"],
    ["guides-admin-statistics", "https://wiki.kavitareader.com/guides/admin-settings/statistics/"],
    ["guides-admin-mediaissues", "https://wiki.kavitareader.com/guides/admin-settings/mediaissues/"],
    ["guides-admin-kavitaplus", "https://wiki.kavitareader.com/guides/admin-settings/kavita+/"],
    ["guides-user-account", "https://wiki.kavitareader.com/guides/user-settings/account/"],
    ["guides-user-preferences", "https://wiki.kavitareader.com/guides/user-settings/preferences/"],
    ["guides-user-reading-profiles", "https://wiki.kavitareader.com/guides/user-settings/reading-profiles/"],
    ["guides-user-3rdparty-clients", "https://wiki.kavitareader.com/guides/user-settings/3rdpartycilents/"],
    ["guides-user-theme", "https://wiki.kavitareader.com/guides/user-settings/theme/"],
    ["guides-user-devices", "https://wiki.kavitareader.com/guides/user-settings/devices/"],
    ["guides-user-profile", "https://wiki.kavitareader.com/guides/user-settings/profile/"],
    ["guides-user-scrobbling", "https://wiki.kavitareader.com/guides/user-settings/scrobbling/"],
    ["guides-features-bookmarks", "https://wiki.kavitareader.com/guides/features/bookmarks/"],
    ["guides-features-customization", "https://wiki.kavitareader.com/guides/features/customization/"],
    ["guides-features-collections", "https://wiki.kavitareader.com/guides/features/collections/"],
    ["guides-features-filtering", "https://wiki.kavitareader.com/guides/features/filtering/"],
    ["guides-features-opds", "https://wiki.kavitareader.com/guides/features/opds/"],
    ["guides-features-readinglists", "https://wiki.kavitareader.com/guides/features/readinglists/"],
    ["guides-features-cbl-import", "https://wiki.kavitareader.com/guides/features/cbl-import/"],
    ["guides-features-relationships", "https://wiki.kavitareader.com/guides/features/relationships/"],
    ["guides-metadata-general", "https://wiki.kavitareader.com/guides/metadata/general/"],
    ["guides-metadata-comics", "https://wiki.kavitareader.com/guides/metadata/comics/"],
    ["guides-metadata-epubs", "https://wiki.kavitareader.com/guides/metadata/epubs/"],
    ["guides-metadata-pdfs", "https://wiki.kavitareader.com/guides/metadata/pdfs/"],
    ["guides-readers-epub", "https://wiki.kavitareader.com/guides/readers/epub/"],
    ["guides-readers-comic-manga", "https://wiki.kavitareader.com/guides/readers/comic-manga/"],
    ["guides-readers-pdf", "https://wiki.kavitareader.com/guides/readers/pdf/"],
    ["guides-scanner", "https://wiki.kavitareader.com/guides/scanner/"],
    ["guides-scanner-managefiles", "https://wiki.kavitareader.com/guides/scanner/managefiles/"],
    ["guides-scanner-comic", "https://wiki.kavitareader.com/guides/scanner/comic/"],
    ["guides-scanner-comicvine", "https://wiki.kavitareader.com/guides/scanner/comicvine/"],
    ["guides-scanner-epub", "https://wiki.kavitareader.com/guides/scanner/epub/"],
    ["guides-scanner-image", "https://wiki.kavitareader.com/guides/scanner/image/"],
    ["guides-scanner-manga", "https://wiki.kavitareader.com/guides/scanner/manga/"],
    ["guides-scanner-pdf", "https://wiki.kavitareader.com/guides/scanner/pdf/"],
    ["guides-3rdparty-aidoku", "https://wiki.kavitareader.com/guides/3rdparty/aidoku/"],
    ["guides-3rdparty-cdisplayex", "https://wiki.kavitareader.com/guides/3rdparty/cdisplayex/"],
    ["guides-3rdparty-kavita-dedicated", "https://wiki.kavitareader.com/guides/3rdparty/kavita-dedicated/"],
    ["guides-3rdparty-komic", "https://wiki.kavitareader.com/guides/3rdparty/komic/"],
    ["guides-3rdparty-koreader", "https://wiki.kavitareader.com/guides/3rdparty/koreader/"],
    ["guides-3rdparty-mylar", "https://wiki.kavitareader.com/guides/3rdparty/mylar/"],
    ["guides-3rdparty-panels", "https://wiki.kavitareader.com/guides/3rdparty/panels/"],
    ["guides-3rdparty-paperback", "https://wiki.kavitareader.com/guides/3rdparty/paperback/"],
    ["guides-3rdparty-tachi-like", "https://wiki.kavitareader.com/guides/3rdparty/tachi-like/"],
    ["guides-3rdparty-yomu", "https://wiki.kavitareader.com/guides/3rdparty/yomu/"],
    ["guides-external-mangamanager", "https://wiki.kavitareader.com/guides/external-tools/mangamanager/"],
    ["guides-external-komf", "https://wiki.kavitareader.com/guides/external-tools/komf/"],
    ["guides-external-comictagger", "https://wiki.kavitareader.com/guides/external-tools/comictagger/"],
    ["guides-external-epub2cbz", "https://wiki.kavitareader.com/guides/external-tools/epub2cbz/"],
    ["guides-external-calibre", "https://wiki.kavitareader.com/guides/external-tools/calibre/"],
    ["guides-external-sigil", "https://wiki.kavitareader.com/guides/external-tools/sigil/"],
    ["guides-external-obsidian", "https://wiki.kavitareader.com/guides/external-tools/obsidian-importer/"],
    ["guides-external-cbz-cover-manager", "https://wiki.kavitareader.com/guides/external-tools/cbz-cover-manager/"],
    ["guides-external-cbz-missing-sequence", "https://wiki.kavitareader.com/guides/external-tools/cbz-missing-sequence-checker/"],
    ["guides-themes", "https://wiki.kavitareader.com/guides/themes/"],
    ["guides-api", "https://wiki.kavitareader.com/guides/api/"],
    ["troubleshooting-faq", "https://wiki.kavitareader.com/troubleshooting/faq/"],
    ["troubleshooting-media-errors", "https://wiki.kavitareader.com/troubleshooting/media-errors/"],
    ["kavitaplus", "https://wiki.kavitareader.com/kavita+/"],
    ["kavitaplus-metadata", "https://wiki.kavitareader.com/kavita+/metadata/"],
    ["kavitaplus-progress-sync", "https://wiki.kavitareader.com/kavita+/progress-sync/"],
    ["kavitaplus-recs-ratings-reviews", "https://wiki.kavitareader.com/kavita+/recs-ratings-reviews/"],
    ["kavitaplus-smart-collections", "https://wiki.kavitareader.com/kavita+/smart-collections/"],
    ["kavitaplus-manage", "https://wiki.kavitareader.com/kavita+/manage/"],
    ["kavitaplus-faq", "https://wiki.kavitareader.com/kavita+/faq/"],
    ["kavitaplus-changelog", "https://wiki.kavitareader.com/kavita+/changelog/"],
    ["contributing", "https://wiki.kavitareader.com/contributing/"],
    ["donating", "https://wiki.kavitareader.com/donating/"],
    ["licenses", "https://wiki.kavitareader.com/licenses/"],
];

// Simple HTML to Markdown converter
function htmlToMarkdown(html, baseUrl) {
    let md = html;
    
    // Remove script and style tags
    md = md.replace(/<script[^>]*>[\s\S]*?<\/script>/gi, '');
    md = md.replace(/<style[^>]*>[\s\S]*?<\/style>/gi, '');
    
    // Code blocks
    md = md.replace(/<pre[^>]*><code[^>]*>([\s\S]*?)<\/code><\/pre>/gi, (m, code) => {
        code = decodeHTMLEntities(code.trim());
        return '\n```\n' + code + '\n```\n';
    });
    md = md.replace(/<pre[^>]*>([\s\S]*?)<\/pre>/gi, (m, code) => {
        code = decodeHTMLEntities(code.trim());
        return '\n```\n' + code + '\n```\n';
    });
    
    // Inline code
    md = md.replace(/<code[^>]*>(.*?)<\/code>/gi, '`$1`');
    
    // Headers
    md = md.replace(/<h1[^>]*>(.*?)<\/h1>/gi, '\n# $1\n');
    md = md.replace(/<h2[^>]*>(.*?)<\/h2>/gi, '\n## $1\n');
    md = md.replace(/<h3[^>]*>(.*?)<\/h3>/gi, '\n### $1\n');
    md = md.replace(/<h4[^>]*>(.*?)<\/h4>/gi, '\n#### $1\n');
    md = md.replace(/<h5[^>]*>(.*?)<\/h5>/gi, '\n##### $1\n');
    md = md.replace(/<h6[^>]*>(.*?)<\/h6>/gi, '\n###### $1\n');
    
    // Bold and italic
    md = md.replace(/<strong[^>]*>(.*?)<\/strong>/gi, '**$1**');
    md = md.replace(/<b[^>]*>(.*?)<\/b>/gi, '**$1**');
    md = md.replace(/<em[^>]*>(.*?)<\/em>/gi, '*$1*');
    md = md.replace(/<i[^>]*>(.*?)<\/i>/gi, '*$1*');
    
    // Links
    md = md.replace(/<a[^>]*href="([^"]*)"[^>]*>(.*?)<\/a>/gi, '[$2]($1)');
    
    // Images
    md = md.replace(/<img[^>]*src="([^"]*)"[^>]*alt="([^"]*)"[^>]*\/?>/gi, '![$2]($1)');
    md = md.replace(/<img[^>]*src="([^"]*)"[^>]*\/?>/gi, '![]($1)');
    
    // Lists - convert li to list items
    md = md.replace(/<li[^>]*>([\s\S]*?)<\/li>/gi, '- $1\n');
    
    // Remove ul/ol tags
    md = md.replace(/<\/?(ul|ol)[^>]*>/gi, '\n');
    
    // Paragraphs
    md = md.replace(/<p[^>]*>([\s\S]*?)<\/p>/gi, '\n$1\n');
    
    // Line breaks
    md = md.replace(/<br\s*\/?>/gi, '\n');
    
    // Horizontal rules
    md = md.replace(/<hr[^>]*\/?>/gi, '\n---\n');
    
    // Blockquotes
    md = md.replace(/<blockquote[^>]*>([\s\S]*?)<\/blockquote>/gi, (m, content) => {
        return content.split('\n').map(line => '> ' + line).join('\n');
    });
    
    // Tables - basic extraction
    md = md.replace(/<table[^>]*>([\s\S]*?)<\/table>/gi, (m, tableContent) => {
        const rows = tableContent.match(/<tr[^>]*>[\s\S]*?<\/tr>/gi) || [];
        let tableMd = '\n';
        rows.forEach((row, idx) => {
            const cells = row.match(/<t[dh][^>]*>([\s\S]*?)<\/t[dh]>/gi) || [];
            const cellTexts = cells.map(c => c.replace(/<\/?t[dh][^>]*>/gi, '').trim());
            tableMd += '| ' + cellTexts.join(' | ') + ' |\n';
            if (idx === 0) {
                tableMd += '| ' + cellTexts.map(() => '---').join(' | ') + ' |\n';
            }
        });
        return tableMd + '\n';
    });
    
    // Remove remaining tags
    md = md.replace(/<[^>]+>/g, '');
    
    // Decode HTML entities
    md = decodeHTMLEntities(md);
    
    // Clean up whitespace
    md = md.replace(/\n{3,}/g, '\n\n');
    md = md.replace(/^\s+/, '');
    md = md.replace(/\s+$/, '');
    
    return md;
}

function decodeHTMLEntities(text) {
    return text
        .replace(/&amp;/g, '&')
        .replace(/&lt;/g, '<')
        .replace(/&gt;/g, '>')
        .replace(/&quot;/g, '"')
        .replace(/&#39;/g, "'")
        .replace(/&nbsp;/g, ' ')
        .replace(/&#x27;/g, "'")
        .replace(/&#x2F;/g, '/');
}

async function fetchPage(url) {
    const resp = await fetch(url, {
        headers: { 'User-Agent': 'Mozilla/5.0 (compatible; wiki-crawler)' }
    });
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    return await resp.text();
}

function extractContent(html) {
    // Try to find the nextra-content div
    const contentMatch = html.match(/class="[^"]*nextra-content[^"]*"[^>]*>([\s\S]*)<\/main>/);
    if (contentMatch) return contentMatch[1];
    
    // Fallback: find main tag
    const mainMatch = html.match(/<main[^>]*>([\s\S]*?)<\/main>/);
    if (mainMatch) return mainMatch[1];
    
    // Last resort: body content
    const bodyMatch = html.match(/<body[^>]*>([\s\S]*?)<\/body>/);
    if (bodyMatch) return bodyMatch[1];
    
    return html;
}

function extractTitle(html) {
    const titleMatch = html.match(/<title[^>]*>(.*?)<\/title>/);
    return titleMatch ? titleMatch[1].trim() : 'Untitled';
}

async function main() {
    const results = [];
    const errors = [];
    
    console.log(`Fetching ${PAGES.length} pages...`);
    
    for (const [slug, url] of PAGES) {
        try {
            const html = await fetchPage(url);
            const title = extractTitle(html);
            const contentHtml = extractContent(html);
            const markdown = htmlToMarkdown(contentHtml, url);
            
            const fullMd = `# ${title}\n\n> Source: ${url}\n\n${markdown}`;
            
            const filePath = path.join(OUTPUT_DIR, `${slug}.md`);
            fs.writeFileSync(filePath, fullMd, 'utf8');
            
            results.push({ slug, url, title, length: fullMd.length, status: 'ok' });
            console.log(`✓ ${slug} (${fullMd.length} chars)`);
        } catch (err) {
            errors.push({ slug, url, error: err.message });
            console.error(`✗ ${slug}: ${err.message}`);
        }
        
        // Small delay to be respectful
        await new Promise(r => setTimeout(r, 200));
    }
    
    // Save results index
    const index = { total: PAGES.length, fetched: results.length, errors: errors.length, results, errors };
    fs.writeFileSync(path.join(OUTPUT_DIR, '_index.json'), JSON.stringify(index, null, 2));
    
    console.log(`\nDone! ${results.length} pages fetched, ${errors.length} errors.`);
}

main().catch(console.error);
