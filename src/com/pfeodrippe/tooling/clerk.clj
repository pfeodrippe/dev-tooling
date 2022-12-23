(ns com.pfeodrippe.tooling.clerk
  (:require
   [clojure.java.io :as io]
   [nextjournal.clerk :as clerk]
   [com.pfeodrippe.tooling.clerk.util :as tool.clerk.util]
   [com.pfeodrippe.tooling.clerk.parser :as tool.parser]
   [com.pfeodrippe.tooling.clerk.var-changes :as tool.var-changes]))

(defn view-index
  [sections]
  (with-meta
    (into [:div.pl-4 {:style {:font-size "120%"}}]
          (for [{:keys [title pages idx]}
                (->> sections (map-indexed (fn [idx m] (assoc m :idx idx))))]
            [:<>
             [:div.text-xl {:class (when-not (zero? idx) [:mt-8])}
              [:b title]]
             (when (seq pages)
               [:ul.columns-2 {:style {:margin-top "5px"
                                       :padding-left "0.3rem"}}
                (for [child-page (remove nil? pages)]
                  (let [{:keys [notebook-name path error]}
                        (tool.parser/xref-info-from-path child-page)]
                    [:li.list-none {:style {:break-inside :avoid-column
                                            ;; We add to style as there is a rule that overrides
                                            ;; if we set these properties through tailwind.
                                            :margin 0
                                            :margin-bottom "0.6rem"}}
                     [:a.block {:href path
                                :class (cond-> tool.parser/link-classes
                                         error (conj :bg-red-300))
                                :style {:color "inherit"}}
                      notebook-name]]))])]))
    {:type `index-viewer}))

(defn add-path-info!
  "Updates stateful paths for use with Clerk.
  Receives a map from keywords to the symbol namespaces.

  E.g.
  {:doc/test-1 `com.pfeodrippe.tooling.experiment.test1}"
  [m]
  (swap! tool.parser/*state update :path-info merge m))

(defn derive-ns->xrefs
  []
  (->> (update-vals (:tags @tool.parser/*state)
                    (fn [v]
                      (->> v
                           (filter #(= (:tag-name (second  %))
                                       :xref))
                           (mapv (comp :xref :metadata eval))
                           set)))
       (filter (comp seq second))
       (into {})))

(defn build?
  "Check if clerk is building the static app."
  []
  (boolean tool.var-changes/*build*))

(tool.clerk.util/add-global-viewers!
 [{:name ::index-viewer
   :pred #(= (type %) `index-viewer)
   :transform-fn
   (comp clerk/mark-presented
         (clerk/update-val #(clerk/with-viewer :html %)))}])

;; Install font to the `public` folder.
(with-open [concourse-font (io/input-stream (io/resource "font/concourse_index_regular.woff2"))]
  (io/make-parents "public/build/concourse_index_regular.woff2")
  (io/copy
   concourse-font
   (io/file "public/build/concourse_index_regular.woff2")))

(comment

  ;; References:
  ;; - https://danielmiessler.com/blog/build-successful-infosec-career/

  ())
