^{:nextjournal.clerk/visibility {:code :hide}}
(ns com.pfeodrippe.tooling.experiment.index
  {:nextjournal.clerk/no-cache true}
  (:require
   [nextjournal.clerk :as clerk]
   [com.pfeodrippe.tooling.clerk :as tool.clerk]))

{::clerk/visibility {:code :hide :result :hide}}

(tool.clerk/add-path-info!
 {:doc/test-1 `com.pfeodrippe.tooling.experiment.test1})

{::clerk/visibility {:code :hide :result :show}}

;; ◊page-name[{:subtitle "Index"}]{Mobile Guide}

(tool.clerk/view-index
 [{:title "Introduction"
   :pages [:doc/ggag
           :doc/test-1]}
  {:title "Services"}
  {:title "Tools"}
  {:title "Domain"}])

{::clerk/visibility {:code :hide :result :hide}}

(comment

  (clerk/serve! {:watch-paths ["src/com/pfeodrippe/tooling/experiment/"]})

  (clerk/build! {:paths ["src/com/pfeodrippe/tooling/experiment/**"]
                 :index "src/com/pfeodrippe/tooling/experiment/index.clj"
                 :bundle true
                 :browse true})

  ;; TODO:
  ;; - [x] Make static app work again

  ())
