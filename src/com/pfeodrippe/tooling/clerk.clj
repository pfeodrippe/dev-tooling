(ns com.pfeodrippe.tooling.clerk
  (:require
   [nextjournal.clerk :as clerk]
   [com.pfeodrippe.tooling.clerk.util :as tool.clerk.util]
   [com.pfeodrippe.tooling.clerk.parser :as tool.parser]))

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
               [:ul.columns-2 {:style {:margin-top "5px"}}
                (for [child-page pages]
                  (let [{:keys [location-name path error]}
                        (tool.parser/xref-info-from-path child-page)]
                    [:li.list-none.break-all {:style {:break-inside :avoid-column
                                                      ;; We add to style as there is a rule that overrides
                                                      ;; if we set these properties through tailwind.
                                                      :margin 0
                                                      :margin-bottom "0.6rem"}}
                     [:a {:href path
                          :class (cond-> tool.parser/link-classes
                                   error (conj :bg-red-300))
                          :style {:color "inherit"}}
                      location-name]]))])]))
    {:type `index-viewer}))

(defn add-path-info!
  "Updates stateful paths for use with Clerk.
  Receives a map from keywords to the symbol namespaces.

  E.g.
  {:doc/test-1 `com.pfeodrippe.tooling.experiment.test1}"
  [m]
  (swap! tool.parser/*state update :path-info merge m))

(tool.clerk.util/add-global-viewers!
 [{:name ::index-viewer
   :pred #(= (type %) `index-viewer)
   :transform-fn
   (comp clerk/mark-presented
         (clerk/update-val #(clerk/with-viewer :html %)))}])

(comment


  ())
