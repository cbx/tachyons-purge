# Tachyons Purge - Design Document

A Babashka CLI tool that scans source files for Tachyons CSS classes and generates a minimized CSS file containing only the classes in use.

## Overview

**Problem:** Including the full Tachyons CSS (~84KB) when only a fraction of classes are used.

**Solution:** Scan Hiccup and HTML files, extract used classes, output purged CSS.

**Runtime:** Babashka - optimized for fast CLI runs (~10ms startup).

## CLI Interface

```
tachyons-purge [options] <directory>

Options:
  -c, --css PATH      Path to tachyons.css (default: bundled)
  -o, --out PATH      Output to file instead of stdout
  -m, --minify        Minify output (default: readable)
  -e, --extensions    File extensions to scan (default: clj,cljs,cljc,html)
  -v, --verbose       Show detailed stats
  -h, --help          Show help
```

**Examples:**

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
# => Original: 84.2KB → Purged: 2.8KB (97% reduction)
```

## Architecture

Three-phase pipeline with pure functions:

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   SCAN      │ ──▶ │   MATCH     │ ──▶ │   EMIT      │
│             │     │             │     │             │
│ Find files  │     │ Filter CSS  │     │ Output CSS  │
│ Extract     │     │ by used     │     │ + stats     │
│ class names │     │ classes     │     │             │
└─────────────┘     └─────────────┘     └─────────────┘
```

**Phase 1: SCAN** - Walk directory, read files, extract potential Tachyons classes
- Input: directory path, file extensions
- Output: `#{:pa3 :bg-blue :flex ...}` (set of class keywords)

**Phase 2: MATCH** - Parse source CSS, keep only rules matching found classes
- Input: tachyons.css content + set of used classes
- Output: filtered CSS rules

**Phase 3: EMIT** - Format output, calculate stats, write
- Input: filtered CSS rules, options
- Output: CSS string to stdout or file

```clojure
(defn purge [opts]
  (let [classes  (scan-directory (:dir opts) (:extensions opts))
        css      (parse-css (slurp (:css-path opts)))
        filtered (filter-css css classes)]
    (emit filtered opts)))
```

## Class Extraction (SCAN Phase)

### Hiccup Patterns

```clojure
;; Pattern 1: Keyword classes (dot notation)
[:div.pa3.bg-blue ...]           ;; extract: pa3, bg-blue

;; Pattern 2: :class attribute with string
[:div {:class "flex items-center"} ...]  ;; extract: flex, items-center

;; Pattern 3: String literals in common forms
(str "pa3 " (when x "bg-blue"))  ;; extract: pa3, bg-blue
(cond-> "pa3" x (str " ma2"))    ;; extract: pa3, ma2
```

### HTML Patterns

```html
<div class="pa3 bg-blue">...</div>
```

### Extraction Strategy

Pragmatic regex rather than AST parsing:

1. **Keyword dots**: `:\w+\.([\w-]+(?:\.[\w-]+)*)` → split on `.`
2. **Class strings**: `(?::class|class=)\s*"([^"]+)"` → split on whitespace
3. **String literals**: `"([^"]*)"` anywhere → split, filter to valid Tachyons names

Step 3 is intentionally broad - grab all quoted strings and filter against known Tachyons class names. This catches dynamic cases like `(when x "bg-blue")` without parsing Clojure.

```clojure
(defn extract-classes [content]
  (->> (concat
         (extract-keyword-classes content)    ;; .pa3.bg-blue
         (extract-class-attrs content)        ;; :class "..."
         (extract-string-literals content))   ;; all "..." strings
       (filter tachyons-class?)               ;; only valid tachyons names
       set))
```

## CSS Parsing & Filtering (MATCH Phase)

### Parsing Approach

Rather than a full CSS parser, we:

1. Split CSS into "blocks" - either single rules or `@media` blocks
2. For each block, extract the selector (class name)
3. Keep blocks where the class is in our used-classes set

```clojure
(defn parse-css-blocks [css-content]
  ;; Returns [{:selector ".pa3" :rule ".pa3 { padding: 1rem; }"}
  ;;          {:selector ".pa3-ns" :rule "@media...{.pa3-ns{...}}" :media "..."}
  ;;          ...]
  )

(defn filter-css [blocks used-classes]
  (->> blocks
       (filter #(used-classes (selector->class (:selector %))))
       (group-by :media)  ;; group @media rules together
       (format-css)))
```

### @media Handling

Tachyons uses suffixes: `-ns` (not-small), `-m` (medium), `-l` (large). We match these as distinct classes. If you use `pa3-ns`, we include the `@media` wrapper.

### Minification

When `--minify` is set, strip whitespace and newlines. Simple string ops, no external tools.

## Project Structure

```
tachyons-purge/
├── bb.edn                    # Babashka config
├── src/
│   └── tachyons_purge/
│       ├── core.clj          # CLI entry point, arg parsing
│       ├── scan.clj          # File walking, class extraction
│       ├── css.clj           # CSS parsing, filtering
│       └── emit.clj          # Output formatting, stats
├── resources/
│   └── tachyons.min.css      # Bundled standard Tachyons
├── test/
│   └── tachyons_purge/
│       ├── scan_test.clj
│       └── css_test.clj
└── tachyons-purge            # Executable wrapper script
```

### bb.edn

```clojure
{:paths ["src" "resources"]
 :deps {}  ;; no external deps needed
 :tasks {test {:doc "Run tests"
               :task (exec 'cognitect.test-runner.api/test)}}}
```

### Executable Wrapper

```bash
#!/usr/bin/env bb
(require '[tachyons-purge.core :as core])
(core/-main *command-line-args*)
```

### Installation Options

1. Clone repo, add to PATH
2. `bbin install` (if using bbin)
3. Copy single uberscript (can flatten to one file if desired)

## Testing

Focus on pure functions:

```clojure
;; scan_test.clj
(deftest extract-keyword-classes-test
  (is (= #{"pa3" "bg-blue"}
         (extract-classes "[:div.pa3.bg-blue \"hello\"]"))))

(deftest extract-class-attr-test
  (is (= #{"flex" "items-center"}
         (extract-classes "[:div {:class \"flex items-center\"}]"))))

;; css_test.clj
(deftest filter-css-test
  (let [css ".pa0{padding:0}.pa3{padding:1rem}.ma3{margin:1rem}"]
    (is (= ".pa3{padding:1rem}"
           (filter-css css #{"pa3"})))))
```

## Error Handling

Fail fast with clear messages:

| Error | Behavior |
|-------|----------|
| Directory doesn't exist | Exit 1, print "Directory not found: X" |
| No files match extensions | Exit 0, warn "No files found", output empty CSS |
| Custom CSS file not found | Exit 1, print "CSS file not found: X" |
| Invalid CSS (parse error) | Exit 1, print line number + context |
| No classes found | Exit 0, warn "No Tachyons classes found" |

## Future Enhancements (Not in Scope)

- Safelist file for dynamic classes
- Watch mode for development
- Source maps
- Additional file types (JSX, Vue, etc.)
