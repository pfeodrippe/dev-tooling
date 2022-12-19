(ns build
  (:require
   [clojure.java.io :as io]
   [clojure.tools.build.api :as b]
   #_[nextjournal.clerk.render.hashing :refer [lookup-url]]
   [org.corfield.build :as bb]))

(def lib 'myname/mylib)
;; if you want a version of MAJOR.MINOR.COMMITS:
(def version (format "1.0.%s" (b/git-count-revs nil)))

(defn package-asset-map [_]
  (let [lookup-url "https://storage.googleapis.com/nextjournal-cas-eu/assets/4AHiU1U3uJCbnLcyv1qxbfFjKqfjz8RqQBZ5bxyLhH8dnxqWSc8tsQHfzCxGggVwwRv8EvZYoVoSuMTm3UdddqoN-viewer.js"
        #_ #_asset-map (slurp lookup-url)]
    (io/make-parents "target/classes/clerk-asset-map.edn")
    (spit "target/classes/clerk-asset-map.edn" {"/js/viewer.js" lookup-url})))

(defn clerk-build [_]
  (package-asset-map {}))
