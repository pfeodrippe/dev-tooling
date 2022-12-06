(ns com.pfeodrippe.tooling.openapi
  (:require
   [clojure.string :as str]
   [clojure.java.shell :as sh]
   [markdown.core :as md])
  (:import
   (org.openapitools.openapidiff.core OpenApiCompare)
   (org.openapitools.openapidiff.core.output MarkdownRender)))

(defn run-bash
  [command]
  (let [{:keys [err out]} (sh/sh "/bin/bash" "-c" command)]
    (println out)
    (println err)
    (str/trim out)))

(defn run-for-previous-commits
  "Callback, a function which receives `idx`, must be a command which saves JSON
  files in the `openapi-IDX.json` format."
  [{:keys [branch iterations callback]
    :or {iterations 20}}]
  (try
    (run-bash (format "git checkout %s" branch))
    (run! (fn [idx]
            (println "Running callback for iteration " idx)
            (callback idx)
            (run-bash "git checkout HEAD^1"))
          (range iterations))
    (finally
      (run-bash (format "git checkout %s" branch)))))

(defn process-openapi-files
  [{:keys [branch iterations]
    :or {iterations 20}}]
  (try
    (run-bash (format "git checkout %s" branch))
    ;; Drop last as we are interested in the diffs.
    (let [mds (->> (drop-last (range iterations))
                   (mapv (fn [idx]
                           (let [tag (run-bash "git describe --tags")
                                 date (run-bash "git show -s --format=%ci HEAD")
                                 commit (run-bash "git rev-parse HEAD")
                                 diff (OpenApiCompare/fromContents
                                       (slurp (format "openapi-%s.json" (inc idx)))
                                       (slurp (format "openapi-%s.json" idx)))
                                 md-str (-> (MarkdownRender.) (.render diff))]
                             (run-bash "git checkout HEAD^1")
                             (when (seq md-str)
                               (str (format "## %s
Commit: `%s`

Commit time: `%s`
\n\n"
                                            tag
                                            commit
                                            date)
                                    md-str)))))
                   (remove nil?))]
      (spit "diff.md"
            (->> mds
                 (str/join "\n----------------------------------------------------------------\n")))
      (md/md-to-html "diff.md" "diff.html"))
    (finally
      (run-bash (format "git checkout %s" branch)))))

(comment

  (run-for-previous-commits
   {:callback #(run-bash (format "lein openapi && mv openapi.json openapi-%s.json" %))
    :branch "main"
    :iterations 1})

  (process-openapi-files
   {:branch "main"
    :iterations 10})

  ())
