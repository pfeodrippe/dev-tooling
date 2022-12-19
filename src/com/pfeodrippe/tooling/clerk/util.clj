(ns com.pfeodrippe.tooling.clerk.util
  (:require
   [nextjournal.clerk.viewer :as clerk.viewer]))

(defn add-global-viewers!
  [viewers]
  (let [updated-viewers (clerk.viewer/add-viewers
                         (->> (clerk.viewer/get-default-viewers)
                              ;; Remove viewers with the same name so you don't have
                              ;; duplicated ones.
                              (remove (comp (set (mapv :name viewers))
                                            :name)))
                         viewers)]
    (clerk.viewer/reset-viewers! :default updated-viewers)))
