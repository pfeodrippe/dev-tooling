(ns com.pfeodrippe.tooling.experiment.path
  (:require
   [com.pfeodrippe.tooling.clerk.parser :as tool.parser]))

(tool.parser/add-path-info!
 {:doc/test-1 `com.pfeodrippe.tooling.experiment.test1})
