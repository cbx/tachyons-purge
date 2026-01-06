# tachyons-purge

A Babashka CLI tool that scans source files for Tachyons CSS classes and generates a minimized CSS file containing only the classes in use.

## Problem

Including the full Tachyons CSS (~72KB) when only a fraction of classes are used.

## Solution

Scan Hiccup and HTML files, extract used classes, output purged CSS.

## Installation

Via [bbin](https://github.com/babashka/bbin):

```bash
bbin install io.github.cbx/tachyons-purge
```

Or clone and run directly:

```bash
git clone https://github.com/cbx/tachyons-purge.git
cd tachyons-purge
./tachyons-purge --help
```

## Usage

```bash
tachyons-purge [options] <directory>

Options:
  -c, --css PATH        Path to tachyons.css (default: bundled v4.12.0)
  -o, --out PATH        Output to file instead of stdout
  -m, --minify          Minify output
  -e, --extensions EXTS File extensions to scan (default: clj,cljs,cljc,html)
  -v, --verbose         Show detailed stats
  -h, --help            Show help
```

## Examples

```bash
# Basic - scan src/, print purged CSS to stdout
tachyons-purge src/

# Production - minified to file
tachyons-purge --minify --out public/css/tachyons.min.css src/

# Custom CSS source
tachyons-purge --css assets/my-tachyons.css src/

# Verbose stats
tachyons-purge -v src/
# => Scanned 47 files
# => Found 83 unique Tachyons classes
# => Original: 72.2KB -> Purged: 2.8KB (96% reduction)
```

## Class Detection

Extracts classes from:

- **Hiccup keyword notation**: `[:div.pa3.bg-blue ...]`
- **Class attributes**: `{:class "flex items-center"}` or `class="flex items-center"`
- **String literals**: `(when active? "bg-green")`

## Requirements

- [Babashka](https://babashka.org/) installed and on PATH

## License

MIT
