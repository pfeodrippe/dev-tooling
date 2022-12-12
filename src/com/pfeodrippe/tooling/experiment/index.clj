^{:nextjournal.clerk/visibility {:code :hide}}
(ns com.pfeodrippe.tooling.experiment.index
  {:nextjournal.clerk/no-cache true}
  (:require
   [nextjournal.clerk :as clerk]
   [com.pfeodrippe.tooling.clerk :as tool.clerk]
   com.pfeodrippe.tooling.experiment.path))

{::clerk/visibility {:code :hide :result :hide}}

{::clerk/visibility {:code :hide :result :show}}

;; ◊page-name[{:subtitle "Index"}]{Mobile Guide}

(tool.clerk/view-index
 [{:title "Introduction"
   :pages ['com.pfeodrippe.tooling.experiment.clerk
           :doc/test-1]}
  {:title "Services"}
  {:title "Tools"}
  {:title "Domain"}])

{::clerk/visibility {:code :hide :result :hide}}
