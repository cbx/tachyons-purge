# Tachyons Purge Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a Babashka CLI tool that scans source files for Tachyons CSS classes and outputs purged CSS.

**Architecture:** Three-phase pipeline (SCAN → MATCH → EMIT) with pure functions. Regex-based class extraction from Hiccup/HTML, simple CSS block parsing, filtered output with stats.

**Tech Stack:** Babashka, clojure.tools.cli for arg parsing, no external dependencies.

---

## Task 1: Project Setup

**Files:**
- Create: `bb.edn`
- Create: `src/tachyons_purge/core.clj`

**Step 1: Create bb.edn**

```clojure
{:paths ["src" "resources" "test"]
 :deps {io.github.cognitect-labs/test-runner
        {:git/tag "v0.5.1" :git/sha "dfb30dd"}}}
```

**Step 2: Create minimal core.clj**

```clojure
(ns tachyons-purge.core
  (:require [clojure.tools.cli :refer [parse-opts]]))

(def cli-options
  [["-c" "--css PATH" "Path to tachyons.css (default: bundled)"]
   ["-o" "--out PATH" "Output to file instead of stdout"]
   ["-m" "--minify" "Minify output"]
   ["-e" "--extensions EXTS" "File extensions to scan"
    :default "clj,cljs,cljc,html"]
   ["-v" "--verbose" "Show detailed stats"]
   ["-h" "--help" "Show help"]])

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) (println "tachyons-purge [options] <directory>\n\nOptions:\n" summary)
      errors (do (doseq [e errors] (println e)) (System/exit 1))
      (empty? arguments) (do (println "Error: directory required") (System/exit 1))
      :else (println "TODO: purge" (first arguments) options))))
```

**Step 3: Verify CLI works**

Run: `bb -m tachyons-purge.core --help`

Expected output:
```
tachyons-purge [options] <directory>

Options:
  -c, --css PATH        Path to tachyons.css (default: bundled)
  -o, --out PATH        Output to file instead of stdout
  -m, --minify          Minify output
  -e, --extensions EXTS File extensions to scan
  -v, --verbose         Show detailed stats
  -h, --help            Show help
```

**Step 4: Commit**

```bash
git add bb.edn src/
git commit -m "Set up project structure with CLI arg parsing"
```

---

## Task 2: Scan Module - File Walking

**Files:**
- Create: `src/tachyons_purge/scan.clj`
- Create: `test/tachyons_purge/scan_test.clj`

**Step 1: Write the failing test**

```clojure
(ns tachyons-purge.scan-test
  (:require [clojure.test :refer [deftest is testing]]
            [tachyons-purge.scan :as scan]
            [babashka.fs :as fs]))

(deftest find-files-test
  (testing "finds files with matching extensions"
    (let [tmp (fs/create-temp-dir)
          clj-file (fs/file tmp "foo.clj")
          html-file (fs/file tmp "bar.html")
          txt-file (fs/file tmp "ignore.txt")]
      (spit clj-file "(ns foo)")
      (spit html-file "<html>")
      (spit txt-file "ignore")
      (let [found (scan/find-files (str tmp) #{"clj" "html"})]
        (is (= 2 (count found)))
        (is (every? #(or (.endsWith % ".clj") (.endsWith % ".html")) found)))
      (fs/delete-tree tmp))))
```

**Step 2: Run test to verify it fails**

Run: `bb -m cognitect.test-runner.api/test`

Expected: FAIL - namespace not found

**Step 3: Write minimal implementation**

```clojure
(ns tachyons-purge.scan
  (:require [babashka.fs :as fs]))

(defn find-files
  "Find all files in directory matching the given extensions."
  [dir extensions]
  (->> (fs/glob dir "**/*")
       (filter fs/regular-file?)
       (map str)
       (filter (fn [path]
                 (some #(clojure.string/ends-with? path (str "." %)) extensions)))
       vec))
```

**Step 4: Run test to verify it passes**

Run: `bb -m cognitect.test-runner.api/test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/tachyons_purge/scan.clj test/
git commit -m "Add file walking with extension filtering"
```

---

## Task 3: Extract Classes from Keyword Notation

**Files:**
- Modify: `src/tachyons_purge/scan.clj`
- Modify: `test/tachyons_purge/scan_test.clj`

**Step 1: Write the failing test**

Add to `scan_test.clj`:

```clojure
(deftest extract-keyword-classes-test
  (testing "extracts classes from keyword dot notation"
    (is (= #{"pa3" "bg-blue"}
           (scan/extract-keyword-classes "[:div.pa3.bg-blue \"hello\"]")))
    (is (= #{"flex" "items-center" "justify-between"}
           (scan/extract-keyword-classes "[:nav.flex.items-center.justify-between]")))
    (is (= #{}
           (scan/extract-keyword-classes "[:div {:class \"pa3\"}]")))))
```

**Step 2: Run test to verify it fails**

Run: `bb -m cognitect.test-runner.api/test`

Expected: FAIL - function not found

**Step 3: Write minimal implementation**

Add to `scan.clj`:

```clojure
(defn extract-keyword-classes
  "Extract classes from Hiccup keyword dot notation like :div.pa3.bg-blue"
  [content]
  (let [pattern #":\w+\.([\w-]+(?:\.[\w-]+)*)"
        matches (re-seq pattern content)]
    (->> matches
         (mapcat (fn [[_ classes]] (clojure.string/split classes #"\.")))
         set)))
```

**Step 4: Run test to verify it passes**

Run: `bb -m cognitect.test-runner.api/test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/tachyons_purge/scan.clj test/tachyons_purge/scan_test.clj
git commit -m "Extract classes from keyword dot notation"
```

---

## Task 4: Extract Classes from :class Attributes

**Files:**
- Modify: `src/tachyons_purge/scan.clj`
- Modify: `test/tachyons_purge/scan_test.clj`

**Step 1: Write the failing test**

Add to `scan_test.clj`:

```clojure
(deftest extract-class-attrs-test
  (testing "extracts classes from :class attributes"
    (is (= #{"flex" "items-center"}
           (scan/extract-class-attrs "[:div {:class \"flex items-center\"}]")))
    (is (= #{"pa3"}
           (scan/extract-class-attrs "[:div {:class \"pa3\"} \"text\"]"))))
  (testing "extracts classes from HTML class attributes"
    (is (= #{"bg-blue" "white"}
           (scan/extract-class-attrs "<div class=\"bg-blue white\">")))))
```

**Step 2: Run test to verify it fails**

Run: `bb -m cognitect.test-runner.api/test`

Expected: FAIL - function not found

**Step 3: Write minimal implementation**

Add to `scan.clj`:

```clojure
(defn extract-class-attrs
  "Extract classes from :class or class= attributes"
  [content]
  (let [pattern #"(?::class|class=)\s*\"([^\"]+)\""
        matches (re-seq pattern content)]
    (->> matches
         (mapcat (fn [[_ classes]] (clojure.string/split classes #"\s+")))
         (remove clojure.string/blank?)
         set)))
```

**Step 4: Run test to verify it passes**

Run: `bb -m cognitect.test-runner.api/test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/tachyons_purge/scan.clj test/tachyons_purge/scan_test.clj
git commit -m "Extract classes from :class and class= attributes"
```

---

## Task 5: Extract Classes from String Literals

**Files:**
- Modify: `src/tachyons_purge/scan.clj`
- Modify: `test/tachyons_purge/scan_test.clj`

**Step 1: Write the failing test**

Add to `scan_test.clj`:

```clojure
(deftest extract-string-literals-test
  (testing "extracts potential classes from string literals"
    (is (= #{"pa3" "bg-blue"}
           (scan/extract-string-literals "(str \"pa3 \" (when x \"bg-blue\"))")))
    (is (= #{"ma2" "pa3"}
           (scan/extract-string-literals "(cond-> \"pa3\" x (str \" ma2\"))")))))
```

**Step 2: Run test to verify it fails**

Run: `bb -m cognitect.test-runner.api/test`

Expected: FAIL - function not found

**Step 3: Write minimal implementation**

Add to `scan.clj`:

```clojure
(defn extract-string-literals
  "Extract all words from string literals that could be class names"
  [content]
  (let [pattern #"\"([^\"]*)\""
        matches (re-seq pattern content)]
    (->> matches
         (mapcat (fn [[_ s]] (clojure.string/split s #"\s+")))
         (remove clojure.string/blank?)
         (filter #(re-matches #"[\w-]+" %))
         set)))
```

**Step 4: Run test to verify it passes**

Run: `bb -m cognitect.test-runner.api/test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/tachyons_purge/scan.clj test/tachyons_purge/scan_test.clj
git commit -m "Extract potential classes from string literals"
```

---

## Task 6: Combined Class Extraction

**Files:**
- Modify: `src/tachyons_purge/scan.clj`
- Modify: `test/tachyons_purge/scan_test.clj`

**Step 1: Write the failing test**

Add to `scan_test.clj`:

```clojure
(deftest extract-classes-test
  (testing "combines all extraction methods"
    (let [content "[:div.pa3.flex {:class \"items-center bg-blue\"}
                    (when active? \"bg-green\")]"]
      (is (= #{"pa3" "flex" "items-center" "bg-blue" "bg-green" "active?"}
             (scan/extract-classes content))))))
```

**Step 2: Run test to verify it fails**

Run: `bb -m cognitect.test-runner.api/test`

Expected: FAIL - function not found

**Step 3: Write minimal implementation**

Add to `scan.clj`:

```clojure
(defn extract-classes
  "Extract all potential class names from content using all methods"
  [content]
  (clojure.set/union
    (extract-keyword-classes content)
    (extract-class-attrs content)
    (extract-string-literals content)))
```

Add require at top: `[clojure.set]`

**Step 4: Run test to verify it passes**

Run: `bb -m cognitect.test-runner.api/test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/tachyons_purge/scan.clj test/tachyons_purge/scan_test.clj
git commit -m "Add combined class extraction function"
```

---

## Task 7: Scan Directory for Classes

**Files:**
- Modify: `src/tachyons_purge/scan.clj`
- Modify: `test/tachyons_purge/scan_test.clj`

**Step 1: Write the failing test**

Add to `scan_test.clj`:

```clojure
(deftest scan-directory-test
  (testing "scans all files and extracts classes"
    (let [tmp (fs/create-temp-dir)
          clj-file (fs/file tmp "foo.clj")
          html-file (fs/file tmp "bar.html")]
      (spit clj-file "[:div.pa3.flex {:class \"ma2\"}]")
      (spit html-file "<div class=\"bg-blue white\">")
      (let [classes (scan/scan-directory (str tmp) #{"clj" "html"})]
        (is (contains? classes "pa3"))
        (is (contains? classes "flex"))
        (is (contains? classes "ma2"))
        (is (contains? classes "bg-blue"))
        (is (contains? classes "white")))
      (fs/delete-tree tmp))))
```

**Step 2: Run test to verify it fails**

Run: `bb -m cognitect.test-runner.api/test`

Expected: FAIL - function not found

**Step 3: Write minimal implementation**

Add to `scan.clj`:

```clojure
(defn scan-directory
  "Scan all matching files in directory and extract classes"
  [dir extensions]
  (let [files (find-files dir extensions)]
    (->> files
         (map slurp)
         (map extract-classes)
         (reduce clojure.set/union #{}))))
```

**Step 4: Run test to verify it passes**

Run: `bb -m cognitect.test-runner.api/test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/tachyons_purge/scan.clj test/tachyons_purge/scan_test.clj
git commit -m "Add directory scanning with class extraction"
```

---

## Task 8: CSS Module - Parse CSS Blocks

**Files:**
- Create: `src/tachyons_purge/css.clj`
- Create: `test/tachyons_purge/css_test.clj`

**Step 1: Write the failing test**

```clojure
(ns tachyons-purge.css-test
  (:require [clojure.test :refer [deftest is testing]]
            [tachyons-purge.css :as css]))

(deftest parse-css-blocks-test
  (testing "parses simple CSS rules"
    (let [css ".pa0 { padding: 0; }\n.pa3 { padding: 1rem; }"
          blocks (css/parse-css-blocks css)]
      (is (= 2 (count blocks)))
      (is (= "pa0" (:class (first blocks))))
      (is (= "pa3" (:class (second blocks)))))))
```

**Step 2: Run test to verify it fails**

Run: `bb -m cognitect.test-runner.api/test`

Expected: FAIL - namespace not found

**Step 3: Write minimal implementation**

```clojure
(ns tachyons-purge.css
  (:require [clojure.string :as str]))

(defn parse-css-blocks
  "Parse CSS into blocks with class names and rules"
  [css-content]
  (let [pattern #"\.([a-zA-Z0-9_-]+)\s*\{[^}]+\}"
        matches (re-seq pattern css-content)]
    (mapv (fn [[rule class-name]]
            {:class class-name
             :rule rule
             :media nil})
          matches)))
```

**Step 4: Run test to verify it passes**

Run: `bb -m cognitect.test-runner.api/test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/tachyons_purge/css.clj test/tachyons_purge/css_test.clj
git commit -m "Add CSS block parsing"
```

---

## Task 9: CSS Module - Handle @media Blocks

**Files:**
- Modify: `src/tachyons_purge/css.clj`
- Modify: `test/tachyons_purge/css_test.clj`

**Step 1: Write the failing test**

Add to `css_test.clj`:

```clojure
(deftest parse-media-blocks-test
  (testing "parses @media blocks"
    (let [css "@media screen and (min-width: 30em) {\n  .pa3-ns { padding: 1rem; }\n}"
          blocks (css/parse-css-blocks css)]
      (is (= 1 (count blocks)))
      (is (= "pa3-ns" (:class (first blocks))))
      (is (str/includes? (:media (first blocks)) "min-width: 30em")))))
```

Add require: `[clojure.string :as str]`

**Step 2: Run test to verify it fails**

Run: `bb -m cognitect.test-runner.api/test`

Expected: FAIL - media is nil

**Step 3: Update implementation**

Replace `parse-css-blocks` in `css.clj`:

```clojure
(defn- parse-simple-rules
  "Parse rules outside @media blocks"
  [css-content]
  (let [;; Remove @media blocks first
        no-media (str/replace css-content #"@media[^{]+\{(?:[^{}]|\{[^{}]*\})*\}" "")
        pattern #"\.([a-zA-Z0-9_-]+)\s*\{[^}]+\}"]
    (->> (re-seq pattern no-media)
         (mapv (fn [[rule class-name]]
                 {:class class-name :rule rule :media nil})))))

(defn- parse-media-rules
  "Parse rules inside @media blocks"
  [css-content]
  (let [media-pattern #"@media\s+([^{]+)\{((?:[^{}]|\{[^{}]*\})*)\}"
        rule-pattern #"\.([a-zA-Z0-9_-]+)\s*\{[^}]+\}"]
    (->> (re-seq media-pattern css-content)
         (mapcat (fn [[full-media media-query inner]]
                   (->> (re-seq rule-pattern inner)
                        (mapv (fn [[rule class-name]]
                                {:class class-name
                                 :rule rule
                                 :media (str/trim media-query)}))))))))

(defn parse-css-blocks
  "Parse CSS into blocks with class names and rules"
  [css-content]
  (vec (concat (parse-simple-rules css-content)
               (parse-media-rules css-content))))
```

**Step 4: Run test to verify it passes**

Run: `bb -m cognitect.test-runner.api/test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/tachyons_purge/css.clj test/tachyons_purge/css_test.clj
git commit -m "Handle @media blocks in CSS parsing"
```

---

## Task 10: CSS Module - Filter by Used Classes

**Files:**
- Modify: `src/tachyons_purge/css.clj`
- Modify: `test/tachyons_purge/css_test.clj`

**Step 1: Write the failing test**

Add to `css_test.clj`:

```clojure
(deftest filter-css-test
  (testing "filters CSS to only used classes"
    (let [css ".pa0 { padding: 0; }\n.pa3 { padding: 1rem; }\n.ma3 { margin: 1rem; }"
          used #{"pa3"}
          result (css/filter-css css used)]
      (is (str/includes? result ".pa3"))
      (is (not (str/includes? result ".pa0")))
      (is (not (str/includes? result ".ma3"))))))
```

**Step 2: Run test to verify it fails**

Run: `bb -m cognitect.test-runner.api/test`

Expected: FAIL - function not found

**Step 3: Write minimal implementation**

Add to `css.clj`:

```clojure
(defn filter-css
  "Filter CSS to only include rules for used classes"
  [css-content used-classes]
  (let [blocks (parse-css-blocks css-content)
        filtered (filter #(used-classes (:class %)) blocks)
        grouped (group-by :media filtered)]
    (str/join "\n"
      (concat
        ;; Non-media rules first
        (map :rule (get grouped nil))
        ;; Then media rules grouped
        (for [[media rules] (dissoc grouped nil)]
          (str "@media " media " {\n"
               (str/join "\n" (map #(str "  " (:rule %)) rules))
               "\n}"))))))
```

**Step 4: Run test to verify it passes**

Run: `bb -m cognitect.test-runner.api/test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/tachyons_purge/css.clj test/tachyons_purge/css_test.clj
git commit -m "Add CSS filtering by used classes"
```

---

## Task 11: Emit Module - Stats and Formatting

**Files:**
- Create: `src/tachyons_purge/emit.clj`
- Create: `test/tachyons_purge/emit_test.clj`

**Step 1: Write the failing test**

```clojure
(ns tachyons-purge.emit-test
  (:require [clojure.test :refer [deftest is testing]]
            [tachyons-purge.emit :as emit]))

(deftest format-stats-test
  (testing "formats reduction stats"
    (let [stats (emit/calc-stats 84000 2800 83)]
      (is (= 84000 (:original-bytes stats)))
      (is (= 2800 (:purged-bytes stats)))
      (is (= 83 (:class-count stats)))
      (is (= 97 (:reduction-pct stats))))))

(deftest minify-test
  (testing "minifies CSS"
    (is (= ".pa3{padding:1rem}.ma3{margin:1rem}"
           (emit/minify ".pa3 { padding: 1rem; }\n.ma3 { margin: 1rem; }")))))
```

**Step 2: Run test to verify it fails**

Run: `bb -m cognitect.test-runner.api/test`

Expected: FAIL - namespace not found

**Step 3: Write minimal implementation**

```clojure
(ns tachyons-purge.emit
  (:require [clojure.string :as str]))

(defn calc-stats
  "Calculate size reduction statistics"
  [original-bytes purged-bytes class-count]
  {:original-bytes original-bytes
   :purged-bytes purged-bytes
   :class-count class-count
   :reduction-pct (int (* 100 (/ (- original-bytes purged-bytes) original-bytes)))})

(defn minify
  "Minify CSS by removing whitespace"
  [css]
  (-> css
      (str/replace #"\s*\{\s*" "{")
      (str/replace #"\s*\}\s*" "}")
      (str/replace #"\s*:\s*" ":")
      (str/replace #"\s*;\s*" ";")
      (str/replace #";\}" "}")
      (str/replace #"\n+" "")))

(defn format-bytes
  "Format bytes as human readable"
  [bytes]
  (cond
    (>= bytes 1024) (format "%.1fKB" (/ bytes 1024.0))
    :else (str bytes "B")))

(defn print-stats
  "Print statistics to stderr"
  [stats file-count]
  (binding [*out* *err*]
    (println (format "Scanned %d files" file-count))
    (println (format "Found %d unique Tachyons classes" (:class-count stats)))
    (println (format "Original: %s -> Purged: %s (%d%% reduction)"
                     (format-bytes (:original-bytes stats))
                     (format-bytes (:purged-bytes stats))
                     (:reduction-pct stats)))))
```

**Step 4: Run test to verify it passes**

Run: `bb -m cognitect.test-runner.api/test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/tachyons_purge/emit.clj test/tachyons_purge/emit_test.clj
git commit -m "Add emit module with stats and minification"
```

---

## Task 12: Bundle Tachyons CSS

**Files:**
- Create: `resources/tachyons.min.css`
- Modify: `src/tachyons_purge/core.clj`

**Step 1: Download Tachyons CSS**

Run:
```bash
mkdir -p resources
curl -sL https://unpkg.com/tachyons@4.12.0/css/tachyons.min.css > resources/tachyons.min.css
```

**Step 2: Add resource loading to core.clj**

Add helper function:

```clojure
(defn get-css-content
  "Get CSS content from path or bundled resource"
  [css-path]
  (if css-path
    (slurp css-path)
    (slurp (io/resource "tachyons.min.css"))))
```

Add require: `[clojure.java.io :as io]`

**Step 3: Verify resource loads**

Run: `bb -e "(require '[clojure.java.io :as io]) (println (count (slurp (io/resource \"tachyons.min.css\"))))"`

Expected: prints byte count (around 84000)

**Step 4: Commit**

```bash
git add resources/tachyons.min.css src/tachyons_purge/core.clj
git commit -m "Bundle Tachyons CSS as resource"
```

---

## Task 13: Wire Everything Together

**Files:**
- Modify: `src/tachyons_purge/core.clj`

**Step 1: Import modules**

Update requires:

```clojure
(ns tachyons-purge.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [tachyons-purge.scan :as scan]
            [tachyons-purge.css :as css]
            [tachyons-purge.emit :as emit]))
```

**Step 2: Implement purge function**

```clojure
(defn parse-extensions
  "Parse comma-separated extensions into set"
  [s]
  (set (str/split s #",")))

(defn get-css-content
  "Get CSS content from path or bundled resource"
  [css-path]
  (if css-path
    (slurp css-path)
    (slurp (io/resource "tachyons.min.css"))))

(defn purge
  "Main purge logic"
  [{:keys [dir css-path extensions minify? verbose? out-path]}]
  (let [exts (parse-extensions extensions)
        files (scan/find-files dir exts)
        classes (scan/scan-directory dir exts)
        original-css (get-css-content css-path)
        filtered-css (css/filter-css original-css classes)
        final-css (if minify? (emit/minify filtered-css) filtered-css)
        stats (emit/calc-stats (count original-css) (count final-css) (count classes))]
    (when verbose?
      (emit/print-stats stats (count files)))
    (if out-path
      (spit out-path final-css)
      (println final-css))))
```

**Step 3: Update -main**

```clojure
(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options)
      (println "tachyons-purge [options] <directory>\n\nOptions:\n" summary)

      errors
      (do (doseq [e errors] (println e)) (System/exit 1))

      (empty? arguments)
      (do (println "Error: directory required") (System/exit 1))

      :else
      (let [dir (first arguments)]
        (if-not (.isDirectory (io/file dir))
          (do (println "Error: Directory not found:" dir) (System/exit 1))
          (purge {:dir dir
                  :css-path (:css options)
                  :extensions (:extensions options)
                  :minify? (:minify options)
                  :verbose? (:verbose options)
                  :out-path (:out options)}))))))
```

**Step 4: Test end-to-end**

Run: `bb -m tachyons-purge.core -v src/`

Expected: Stats printed, CSS output

**Step 5: Commit**

```bash
git add src/tachyons_purge/core.clj
git commit -m "Wire all modules together in main"
```

---

## Task 14: Create Executable Wrapper

**Files:**
- Create: `tachyons-purge`

**Step 1: Create executable script**

```bash
#!/usr/bin/env bb

(require '[tachyons-purge.core :as core])
(apply core/-main *command-line-args*)
```

**Step 2: Make executable**

Run: `chmod +x tachyons-purge`

**Step 3: Test it**

Run: `./tachyons-purge --help`

Expected: Help output

**Step 4: Commit**

```bash
git add tachyons-purge
git commit -m "Add executable wrapper script"
```

---

## Task 15: Integration Test with Real Files

**Files:**
- Create: `test/integration/sample.clj`
- Create: `test/integration/sample.html`

**Step 1: Create sample files**

`test/integration/sample.clj`:
```clojure
(ns sample
  (:require [hiccup.core :refer [html]]))

(defn page []
  [:div.pa3.bg-blue.white
   [:h1.f2.mb3 "Hello"]
   [:nav.flex.items-center.justify-between
    [:a {:class "link dim"} "Home"]]])
```

`test/integration/sample.html`:
```html
<div class="pa4 bg-near-white">
  <p class="lh-copy measure">Some text</p>
</div>
```

**Step 2: Run purge on test directory**

Run: `./tachyons-purge -v test/integration/`

Expected output includes: pa3, bg-blue, white, f2, mb3, flex, items-center, justify-between, link, dim, pa4, bg-near-white, lh-copy, measure

**Step 3: Verify minified output**

Run: `./tachyons-purge -m test/integration/ | head -c 200`

Expected: Minified CSS without whitespace

**Step 4: Commit**

```bash
git add test/integration/
git commit -m "Add integration test samples"
```

---

## Done!

All tasks complete. The tool is ready to use:

```bash
# Basic usage
./tachyons-purge src/

# Production build
./tachyons-purge --minify --out public/css/tachyons.min.css src/

# Verbose stats
./tachyons-purge -v src/
```
