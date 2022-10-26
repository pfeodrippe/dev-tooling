(ns com.pfeodrippe.tooling.instrumentation
  (:require
   [clojure.string :as str]))

(defn find-vars
  "Find vars in your project according to one or more of the following queries:
  - `:namespaces` (collection of namespaces in symbol or string format)
  - `:ns-meta` (namespaces which contain this metadata)
  - `:ns-prefix` (namespaces with this prefix)
  - `:vars` (collection of vars in symbol or string format)
  - `:var-meta` (vars which contain this metadata)"
  [{:keys [namespaces ns-meta ns-prefix vars var-meta]}]
  (when (or (seq namespaces) ns-meta (seq ns-prefix) (seq vars) var-meta)
    (let [namespaces-set (set (mapv str namespaces))
          vars-set (set (mapv (comp str symbol) vars))]
      (cond->> (all-ns)
        (seq ns-prefix)  (filter #(str/starts-with? % ns-prefix))
        (seq namespaces) (filter #(contains? namespaces-set (str %)))
        ns-meta          (filter #(-> % meta ns-meta))
        true             (mapv ns-interns)
        true             (mapcat vals)
        (seq vars)       (filter #(contains? vars-set (str (symbol %))))
        var-meta         (filter #(-> % meta var-meta))))))

(defonce *var->info (atom {}))

(defn instrument!
  "Instrument vars of your choice.

  `:mode` can be `:instrument` to instrument or `:unstrument` to remove the
  instrumentation.

  See `find-vars`."
  [vars {:keys [mode]}]
  (doseq [v vars]
    (case mode
      :instrument
      (let [original-fn (or (::original-fn (meta v))
                            (deref v))
            input-output* (atom [])]
        (when (and (not (::input-output (meta v)))
                   (fn? original-fn))
          (alter-meta! v assoc
                       ::original-fn original-fn
                       ::input-output input-output*
                       ::original-meta (meta v))
          (alter-var-root v (constantly (fn [& args]
                                          (let [value (apply original-fn args)]
                                            (swap! *var->info update-in [v :input-output]
                                                   (comp vec conj) {:input args
                                                                    :output value})
                                            (swap! input-output* conj {:input args
                                                                       :output value})
                                            value))))))

      :unstrument
      (when-let [original-fn (::original-fn (meta v))]
        (when (:doc (::original-meta (meta v)))
          (alter-meta! v assoc :doc (:doc (::original-meta (meta v)))))
        (alter-meta! v dissoc ::original-fn ::input-output ::original-meta :malli/schema)
        (alter-var-root v (constantly original-fn))))))

(defn get-var-info
  [v]
  (get @*var->info v))
